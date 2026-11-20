/*
 * The MIT License (MIT)
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

import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.workinghours.model.TimeRange;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Per-user working hours, configured on the user's own configuration page.
 *
 * <p>The schedule only has an effect when the user holds
 * {@link WorkingHoursPermissions#ENFORCE_USER_SCHEDULE}. When no times are
 * configured, the global schedule from {@link WorkingHoursConfig} is used.
 */
public class WorkingHoursUserProperty extends UserProperty {

    /**
     * The list of times during which this user may trigger builds.
     */
    private List<TimeRange> buildTimeMatrix;

    /**
     * Gets the user's allowable build times.
     *
     * @return the configured times, or an empty list if none are configured.
     */
    public List<TimeRange> getBuildTimeMatrix() {
        return buildTimeMatrix == null
                ? Collections.<TimeRange>emptyList()
                : buildTimeMatrix;
    }

    /**
     * Sets the user's allowable build times.
     *
     * @param buildTimeMatrix the times during which the user may trigger builds.
     */
    @DataBoundSetter
    public void setBuildTimeMatrix(List<TimeRange> buildTimeMatrix) {
        this.buildTimeMatrix = buildTimeMatrix;
    }

    /**
     * Descriptor which controls whether the property is offered on the user
     * configuration page and how it is instantiated.
     */
    @Extension
    public static class DescriptorImpl extends UserPropertyDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.WorkingHoursUserProperty_DescriptorImpl_DisplayName();
        }

        /**
         * Only offer the property to users allowed to manage their own hours.
         *
         * @return true if the current user may configure their own hours.
         */
        @Override
        public boolean isEnabled() {
            User current = User.current();
            return current != null
                    && current.hasPermission(WorkingHoursPermissions.MANAGE_USER_SCHEDULE);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public UserProperty newInstance(User user) {
            return new WorkingHoursUserProperty();
        }
    }
}
