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

import hudson.security.Permission;
import hudson.security.PermissionGroup;
import jenkins.model.Jenkins;

/**
 * Permissions contributed by the working hours plugin.
 *
 * <p>Both permissions are implied by {@link Jenkins#ADMINISTER}, and can be
 * granted to groups with any authorization strategy (matrix authorization,
 * role strategy, LDAP/AD group mappings, and so on).
 */
public final class WorkingHoursPermissions {

    private WorkingHoursPermissions() {
    }

    /**
     * Permission group shown on the security configuration page.
     */
    public static final PermissionGroup PERMISSIONS = new PermissionGroup(
            WorkingHoursConfig.class,
            Messages._WorkingHoursPermission_Permissions_Title());

    /**
     * Allows a user to configure their own working hours on their user page.
     */
    public static final Permission MANAGE_USER_SCHEDULE = new Permission(
            PERMISSIONS,
            "ManageUserSchedule",
            Messages._WorkingHoursPermission_ManageUserSchedule_Description(),
            Jenkins.ADMINISTER);

    /**
     * Subjects builds triggered by the user to that user's own working hours
     * instead of the job-level schedule.
     */
    public static final Permission ENFORCE_USER_SCHEDULE = new Permission(
            PERMISSIONS,
            "EnforceUserSchedule",
            Messages._WorkingHoursPermission_EnforceUserSchedule_Description(),
            Jenkins.ADMINISTER);
}
