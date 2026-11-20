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
package test.org.jenkinsci.plugins.workinghours;

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.queue.CauseOfBlockage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.jenkinsci.plugins.workinghours.WorkingHoursConfig;
import org.jenkinsci.plugins.workinghours.WorkingHoursPermissions;
import org.jenkinsci.plugins.workinghours.WorkingHoursQueueTaskDispatcher;
import org.jenkinsci.plugins.workinghours.WorkingHoursUserProperty;
import org.jenkinsci.plugins.workinghours.model.TimeRange;
import test.org.jenkinsci.plugins.workinghours.utility.TimeRangeUtility;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Verifies user-level working hours and the abort-outside-hours option.
 */
public class WorkingHoursUserScheduleTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    /**
     * Verifies the permissions are contributed and implied by administer.
     */
    @Test
    public void testPermissionsExist() {
        assertNotNull(WorkingHoursPermissions.PERMISSIONS);
        assertNotNull(WorkingHoursPermissions.MANAGE_USER_SCHEDULE);
        assertNotNull(WorkingHoursPermissions.ENFORCE_USER_SCHEDULE);
        assertTrue("administer should imply the plugin permissions",
                jenkins.jenkins.hasPermission(WorkingHoursPermissions.ENFORCE_USER_SCHEDULE));
    }

    /**
     * Verifies the user property defaults to no configured times.
     */
    @Test
    public void testUserPropertyDefaults() {
        WorkingHoursUserProperty property = new WorkingHoursUserProperty();
        assertTrue(property.getBuildTimeMatrix().isEmpty());
    }

    /**
     * Verifies user level hours block a build which the global schedule allows.
     */
    @Test
    public void testUserScheduleBlocksOutsideUserHours() throws Exception {
        WorkingHoursConfig config = WorkingHoursConfig.get();
        config.setBuildTimeMatrix(TimeRangeUtility.getInclusiveRange());
        config.setAbortOutsideHours(false);

        createUserWithTimes("alice", TimeRangeUtility.getExclusiveRange());

        Queue.WaitingItem item = itemForUser("alice");
        WorkingHoursQueueTaskDispatcher instance = new WorkingHoursQueueTaskDispatcher();

        assertNotNull(instance.canRun(item));
    }

    /**
     * Verifies user level hours allow a build which the global schedule blocks.
     */
    @Test
    public void testUserScheduleAllowsInsideUserHours() throws Exception {
        WorkingHoursConfig config = WorkingHoursConfig.get();
        config.setBuildTimeMatrix(TimeRangeUtility.getExclusiveRange());
        config.setAbortOutsideHours(false);

        createUserWithTimes("bob", TimeRangeUtility.getInclusiveRange());

        Queue.WaitingItem item = itemForUser("bob");
        WorkingHoursQueueTaskDispatcher instance = new WorkingHoursQueueTaskDispatcher();

        assertNull(instance.canRun(item));
    }

    /**
     * Verifies a user without configured times falls back to the global schedule.
     */
    @Test
    public void testUserWithoutTimesUsesJobSchedule() {
        WorkingHoursConfig config = WorkingHoursConfig.get();
        config.setBuildTimeMatrix(TimeRangeUtility.getInclusiveRange());
        config.setAbortOutsideHours(false);

        User.getById("carol", true);

        Queue.WaitingItem item = itemForUser("carol");
        WorkingHoursQueueTaskDispatcher instance = new WorkingHoursQueueTaskDispatcher();

        assertNull("no job property and no user hours - build is not restricted",
                instance.canRun(item));
    }

    /**
     * Verifies the blockage message reflects the abort setting.
     */
    @Test
    public void testAbortOutsideHoursReportsCancellation() throws Exception {
        WorkingHoursConfig config = WorkingHoursConfig.get();
        config.setBuildTimeMatrix(TimeRangeUtility.getExclusiveRange());
        config.setAbortOutsideHours(true);

        createUserWithTimes("dave", TimeRangeUtility.getExclusiveRange());

        Queue.WaitingItem item = itemForUser("dave");
        WorkingHoursQueueTaskDispatcher instance = new WorkingHoursQueueTaskDispatcher();

        CauseOfBlockage blockage = instance.canRun(item);
        assertNotNull(blockage);
        assertTrue("expected a cancellation message but was: " + blockage.getShortDescription(),
                blockage.getShortDescription().toLowerCase().contains("cancel"));
    }

    /**
     * Verifies the user property descriptor is registered and its view is
     * packaged with the plugin.
     */
    @Test
    public void testUserPropertyDescriptorIsRegistered() {
        WorkingHoursUserProperty.DescriptorImpl descriptor =
                jenkins.jenkins.getDescriptorByType(WorkingHoursUserProperty.DescriptorImpl.class);

        assertNotNull("user property descriptor should be registered", descriptor);
        assertNotNull("descriptor should have a display name", descriptor.getDisplayName());
        assertNotNull("config.jelly should be packaged with the plugin",
                WorkingHoursUserProperty.class.getResource("WorkingHoursUserProperty/config.jelly"));
    }

    /**
     * Verifies the global configuration page renders with the new abort option.
     */
    @Test
    public void testGlobalConfigPageRenders() throws Exception {
        // goTo throws if the page does not return 200, which catches Jelly errors
        jenkins.createWebClient().goTo("configure");
    }

    private void createUserWithTimes(String id, List<TimeRange> times) throws IOException {
        User user = User.getById(id, true);
        WorkingHoursUserProperty property = new WorkingHoursUserProperty();
        property.setBuildTimeMatrix(times);
        user.addProperty(property);
        user.save();
    }

    private Queue.WaitingItem itemForUser(String userId) {
        ExecutorStepExecution.PlaceholderTask task =
                mock(ExecutorStepExecution.PlaceholderTask.class);
        WorkflowJob job = mock(WorkflowJob.class);
        WorkflowRun run = mock(WorkflowRun.class);

        when(task.getOwnerTask()).thenReturn(job);
        when(task.run()).thenReturn((Run) run);

        List<Action> actions = new ArrayList<>();
        actions.add(new CauseAction(new Cause.UserIdCause(userId)));

        return new Queue.WaitingItem(Calendar.getInstance(), task, actions);
    }
}
