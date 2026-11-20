/*
 * The MIT License
 *
 * Copyright (c) 2018 GoDaddy Operating Company, LLC.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.workinghours;

import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workinghours.actions.EnforceBuildScheduleAction;
import org.jenkinsci.plugins.workinghours.model.ExcludedDate;
import org.jenkinsci.plugins.workinghours.model.TimeRange;
import hudson.Extension;
import hudson.model.Actionable;
import hudson.model.Cause;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;

/**
 *
 * @author jeffpearce
 */

/**
 * QueueTaskDispatcher implementation that can block jobs configured with the
 * enforceBuildSchedule property outside of configured working hours.
 * 
 * @author jeffpearce
 */
@Extension
public class WorkingHoursQueueTaskDispatcher extends QueueTaskDispatcher {

    /**
     * {@inheritDoc}
     * Determines whether the job has a EnforceScheduleJobProperty,
     * and if so, checks whether the current time is allowed, blocking it if not
     */
    @Override
    public CauseOfBlockage canRun(Queue.Item item) {

        // Don't block tasks which don't have owners. This filters out both
        // freestyle jobs and the task for the Job. The latter is important
        // because we want to block the Run not the Job.
        if (item.task == item.task.getOwnerTask()) {
            return super.canRun(item);
        }

        // We will only consider blocking PlaceholderTasks. We can get the
        // run directly from such tasks.
        if (!(item.task instanceof ExecutorStepExecution.PlaceholderTask)) {
            return super.canRun(item);
        }
        Task ownerTask = item.task.getOwnerTask();
        if (ownerTask instanceof WorkflowJob) {
            WorkflowJob workflowJob = (WorkflowJob) ownerTask;
            Run workflowRun = ((ExecutorStepExecution.PlaceholderTask)item.task).run();

            // User-level working hours take precedence when the triggering user
            // holds the permission and has configured their own times.
            List<TimeRange> userTimes = getUserTimeRanges(item);
            if (userTimes != null) {
                if (!canRunNow(workflowRun, item, userTimes)) {
                    log(Level.INFO, "Blocking item %d (user working hours)", item.getId());
                    return blockOrAbort(item);
                }
                return super.canRun(item);
            }

            EnforceScheduleJobProperty prop = workflowJob.getProperty(EnforceScheduleJobProperty.class);
            if (prop != null) {
                if (prop.getBranches() == null || prop.getBranches().size() == 0
                        || !(workflowJob.getParent() instanceof WorkflowMultiBranchProject)
                        || prop.getBranches().contains(workflowJob.getDisplayName())) {
                    if (!canRunNow(workflowRun, item)) {
                        log(Level.INFO, "Blocking item %d", item.getId());
                        return blockOrAbort(item);
                    }
                }
            }
        }
        return super.canRun(item);
    }

    /**
     * Resolves the working hours that apply to the user who triggered the
     * given queue item.
     *
     * @param item the queue item being considered.
     * @return the triggering user's configured times, or null if user level
     *         enforcement does not apply (no user, missing permission, or no
     *         times configured).
     */
    private List<TimeRange> getUserTimeRanges(Queue.Item item) {
        User user = getTriggeringUser(item);
        if (user == null) {
            return null;
        }
        if (!user.hasPermission(WorkingHoursPermissions.ENFORCE_USER_SCHEDULE)) {
            return null;
        }
        WorkingHoursUserProperty property = user.getProperty(WorkingHoursUserProperty.class);
        if (property == null || property.getBuildTimeMatrix().isEmpty()) {
            return null;
        }
        return property.getBuildTimeMatrix();
    }

    /**
     * Determines which user caused the queue item to be created.
     *
     * @param item the queue item being considered.
     * @return the triggering user, or null if the item was not triggered by a
     *         user (for example a timer or an upstream job).
     */
    private User getTriggeringUser(Queue.Item item) {
        for (Cause cause : item.getCauses()) {
            if (cause instanceof Cause.UserIdCause) {
                String id = ((Cause.UserIdCause) cause).getUserId();
                if (id == null) {
                    continue;
                }
                User user = User.getById(id, false);
                if (user != null) {
                    return user;
                }
            }
        }
        return null;
    }

    /**
     * Builds the blockage for an item which is not allowed to run right now,
     * cancelling it instead when the configuration asks for that.
     *
     * @param item the queue item being blocked.
     * @return the blockage to report to the queue.
     */
    private CauseOfBlockage blockOrAbort(final Queue.Item item) {
        final WorkingHoursConfig config = WorkingHoursConfig.get();
        if (config != null && config.isAbortOutsideHours()) {
            abort(item);
            return new CauseOfBlockage() {
                @Override
                public String getShortDescription() {
                    return Messages.WorkingHoursQueueTaskDispatcher_Aborted();
                }
            };
        }
        return new CauseOfBlockage() {
            @Override
            public String getShortDescription() {
                return config == null ? "" : config.getJobHoldText();
            }
        };
    }

    /**
     * Cancels a queue item, deferred so that the queue is not modified while it
     * is iterating over its dispatchers.
     *
     * @param item the queue item to cancel.
     */
    private void abort(final Queue.Item item) {
        log(Level.WARNING, "Cancelling queue item %d: scheduled outside working hours", item.getId());
        Timer.get().schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    Jenkins.get().getQueue().cancel(item);
                } catch (RuntimeException ex) {
                    log(Level.WARNING, "Unable to cancel queue item %d: %s", item.getId(), ex.getMessage());
                }
            }
        }, 0, TimeUnit.SECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
        return super.canTake(node, item);
    }

    /**
     * Determines if a queue item has been manually released.
     * @param action The enforce build schedule action.
     * @param item The queue item to check.
     * @return true if the item was manually released; false otherwise.
     */
    public boolean isReleased(EnforceBuildScheduleAction action,
            Queue.Item item) {
        return action != null && action.isReleased();
    }

    /**
     * Determines whether a queue item can run at the current moment.
     * @param itemActionable The item being checked.
     * @param item The queue item to check.     
     * @return true if the item can run now; false otherwise.
     */
    public boolean canRunNow(Actionable itemActionable,
            Queue.Item item) {
        WorkingHoursConfig config = WorkingHoursConfig.get();
        List<TimeRange> allowableTimes = config == null
                ? Collections.<TimeRange>emptyList()
                : config.getBuildTimeMatrix();
        return canRunNow(itemActionable, item, allowableTimes);
    }

    /**
     * Determines whether a queue item can run at the current moment against a
     * specific set of allowable times.
     * @param itemActionable The item being checked.
     * @param item The queue item to check.
     * @param allowableTimes The time ranges to check against.
     * @return true if the item can run now; false otherwise.
     */
    public boolean canRunNow(Actionable itemActionable,
            Queue.Item item,
            List<TimeRange> allowableTimes) {

        if (itemActionable == null || item == null) {
            return true;
        }
        Calendar now = Calendar.getInstance();

        EnforceBuildScheduleAction action = itemActionable.getAction(EnforceBuildScheduleAction.class);

        WorkingHoursConfig config = WorkingHoursConfig.get();

        // Check whether today should be excluded according to the excluded dates we set.
        if (config != null) {
            for (ExcludedDate excludedDate : config.getExcludedDates()) {
                if (excludedDate.shouldExclude(now)) {
                    return false;
                }
            }
        }

        for (TimeRange allowableTime : allowableTimes) {
            if (allowableTime.includesTime(now)) {
                if (action != null) {
                    // Mark the action as released for book-keeping
                    action.markReleased();
                }
                return true;
            }
        }
        if (action == null) {
            action = new EnforceBuildScheduleAction();
            itemActionable.addAction(action);
        }

        if (isReleased(action, item)) {
            return true;
        }
        return false;
    }

    private static void log(Level level, String format, Object... args) {
        getLogger().log(level, String.format(format, args));
    }

    private static Logger getLogger() {
        return Logger.getLogger(WorkingHoursQueueTaskDispatcher.class.getName());
    }
}
