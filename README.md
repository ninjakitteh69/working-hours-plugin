# Working Hours Jenkins Plugin

[![Release](https://img.shields.io/github/v/release/ninjakitteh69/working-hours-plugin?sort=semver)](https://github.com/ninjakitteh69/working-hours-plugin/releases)

The working hours plugin allows you to set up a schedule of allowable build times;
projects can opt in to use the schedule to prevent them from running outside of
configured allowable build times. If a build is scheduled during non-working hours
then it is kept in the build queue until the next allowable time.

This build is distributed as a GitHub release asset and is **not** published to the
Jenkins update center — see [Installation](#installation).

Jobs opt in via the `enforceBuildSchedule` job parameter, which is provided by this
plugin. It can optionally take in a `branches` parameter to limit it's usage to only those branches.
This only works in MultiBranchPipelines.

## Requirements

* Jenkins **2.580.1** or newer
* Java **21** or newer, on the controller and on agents

Earlier releases of this plugin (1.1 and older) built against Jenkins 2.150.1 and Java 8.
The minimum versions were raised to follow Jenkins' [Java support policy](https://www.jenkins.io/doc/book/platform-information/support-policy-java/):
Java 17 reached end of support with Jenkins 2.544, so Java 21 is now the minimum.

## Installation

Download the plugin:

**https://github.com/ninjakitteh69/working-hours-plugin/releases/latest/download/working-hours.hpi**

That URL always serves the newest release. To pin a specific version, replace
`latest/download` with `download/2.0.0` (see
[all releases](https://github.com/ninjakitteh69/working-hours-plugin/releases)).

Because this build does not come from the update center, it is installed with one
of the methods below rather than from `Manage Jenkins` → `Plugins` → `Available`.
Installing requires `Overall/Administer`.

### From the Jenkins UI

1. `Manage Jenkins` → `Plugins` → `Advanced settings`
2. Under **Deploy Plugin**, either choose the downloaded `working-hours.hpi`, or
   paste the download URL above
3. Click **Deploy**, then restart Jenkins when prompted
4. Confirm the version under `Plugins` → `Installed plugins`

When installing from the UI, Jenkins resolves the plugin's dependencies from the
update center automatically.

### From the CLI

```bash
java -jar jenkins-cli.jar -s https://jenkins.example.com/ -auth myuser:MY_API_TOKEN \
  install-plugin https://github.com/ninjakitteh69/working-hours-plugin/releases/latest/download/working-hours.hpi \
  -deploy
```

### From the filesystem

```bash
sudo systemctl stop jenkins
sudo curl -fL -o /var/lib/jenkins/plugins/working-hours.jpi \
  https://github.com/ninjakitteh69/working-hours-plugin/releases/latest/download/working-hours.hpi
sudo chown jenkins:jenkins /var/lib/jenkins/plugins/working-hours.jpi
sudo systemctl start jenkins
```

Remove any existing `plugins/working-hours.jpi` and exploded
`plugins/working-hours/` directory first. This method does **not** install
dependencies — make sure these are at least the versions below:

```
workflow-api                1429.v7a_1b_efb_a_f7c5
workflow-durable-task-step  1479.v56e587f413a_7
workflow-job                1624.ve973eb_34e031
workflow-multibranch        842.v3a_b_59b_57b_e6e
workflow-step-api           724.v538c2362b_dfb_
workflow-support            1028.vc22910142650
script-security             1436.v1082791135e4
structs                     362.va_b_695ef4fdf9
```

### Upgrading from 1.x

- 1.x required Jenkins 2.150.1 and Java 8; this release requires **2.580.1 and Java 21**
- Existing global configuration, excluded dates and job properties are preserved
- The plugin is installed with the same artifact ID, so it replaces 1.x in place

## Development

### Building

The build uses the [Maven Wrapper](https://maven.apache.org/wrapper/), so Maven does not need to be installed:

```bash
./mvnw clean verify
```

This compiles the plugin, runs the unit and Jenkins test harness (`JenkinsRule`) functional tests,
runs SpotBugs, and produces `target/working-hours.hpi`.

To build with your own Maven instead of the wrapper, use Maven **3.9.6 or newer** on **JDK 21**.
Note that Maven 3.9.12 currently fails to load the Jenkins `hpi` packaging extension
(`Unknown packaging: hpi`), so use 3.9.9 - 3.9.11, or just use `./mvnw`.

### Running a local Jenkins

```bash
./mvnw hpi:run
```

Then browse to <http://localhost:8080/jenkins/>. Both `target/` and the `work/` home directory are ignored by git.

### Skipping tests

```bash
./mvnw -DskipTests clean package
```

### Continuous integration

`.github/workflows/build.yml` builds the plugin **on tag pushes only** — branch
pushes do not start a build. There is also a manual run that takes a commit:

- Push a tag: `git tag v2.0.0 && git push origin v2.0.0` (builds the tagged commit)
- Or open `Actions` → `Build` → `Run workflow` and enter the **commit SHA** to build.

The workflow checks out the requested commit, verifies the checked out `HEAD`
matches it, runs `./mvnw -B -ntp clean verify` on Temurin 21, and uploads
`working-hours.hpi` as an artifact named `working-hours-<short sha>`.

Note that the commit only selects *which commit* is built; the version reported by
the built plugin still comes from `pom.xml`.

### Publishing a release

Tag builds upload the HPI as a workflow artifact. To turn one into an installable
link, attach that HPI to a GitHub Release for the tag:

```bash
gh release create 2.0.0 working-hours.hpi#working-hours.hpi --verify-tag \
  --title "2.0.0" --notes "Release notes"
```

That publishes
`https://github.com/ninjakitteh69/working-hours-plugin/releases/download/2.0.0/working-hours.hpi`,
which can be pasted straight into the Jenkins **Deploy Plugin** form.

## Usage

Sample job (scripted pipeline):
```
node {
  properties([enforceBuildSchedule()])
  stage('Do some stuff') {
    echo 'this can wait til morning'
  }
}
```

Sample job (declarative pipeline):
```
pipeline {
  agent any
  options {
    enforceBuildSchedule()
  }

  stages {
    stage('Do some stuff') {
      steps {
        echo 'this can wait til morning'
      }
    }
  }
}
```

Sample job with branches parameter (works in both declarative and scripted):
```
node {
  properties([enforceBuildSchedule(branches: ['dev', 'qa', 'prod'])])
  stage('Do some stuff') {
    echo 'this can wait til morning'
  }
}
```

Alternatively, working hours can be enforced per user rather than per job — see
[Per-user working hours](#per-user-working-hours).

When the job runs outside of configured hours, you'll be able to see a tooltip from the job in the queue:

![Queued job](images/queued-job.png "Tooltip for blocked job")

You can also see why the job is blocked in the build log:

![Blocked job](images/blocked-job.png "Build log when a job is blocked")

## Releasing a blocked job

If you want a job to run anyway, you can use the `Release job` action which is
available from various places, such as the build action dropdown:

![Release job action](images/release-build-action-1.png "Release job dropdown")

and the build page:

![Release job action](images/release-build-action-2.png "Release job actions")

## Per-user working hours

Instead of opting individual jobs in, working hours can be enforced per **user**
and granted to a **group** through Jenkins permissions. This is useful when a
team may only deploy during their own hours, regardless of which job they run.

Two permissions are contributed, implied by `Overall/Administer` and assignable
to any group with matrix authorization, role strategy or your security realm's
group mapping:

| Permission | Effect |
|---|---|
| `Working Hours/ManageUserSchedule` | Shows the Working Hours section on the user's own configuration page, so they can set their own times. |
| `Working Hours/EnforceUserSchedule` | Builds triggered by the user are restricted to that user's own times instead of the job-level schedule. |

To set it up:

1. Grant both permissions to the group (for example `deployers`) in
   `Manage Jenkins` → `Security` (any authorization strategy, for example
   matrix authorization or role strategy).
2. Each member configures their times at `Manage Jenkins` → `Users` → their user
   → `Configure`, in the **Working Hours** section.
3. Leave the job without the `enforceBuildSchedule` property, or keep it — the
   user's schedule takes precedence when the triggering user holds the
   permission and has configured at least one time range.

Behaviour notes:

- Times are local to the controller.
- Global *excluded dates* still apply to user schedules.
- If a user has the permission but configured no times, the job-level behaviour
  applies (opt-in via `enforceBuildSchedule`).
- Builds started by a timer, a poll, or an upstream job carry no user identity,
  so they are handled by the job-level rules only. Pair this with the option
  below if timer-driven deployments must never run outside hours.

## Failing instead of queueing

By default, builds queued outside working hours are **held** until the next
allowable time. Enable `Cancel builds scheduled outside working hours` in
`Manage Jenkins` → `System` → Working Hours to change that.

When enabled, such builds are cancelled before they start rather than deferred.
This is the option to use when a deployment must never run outside working
hours: a timer-triggered deployment at 02:00 is cancelled instead of quietly
running at 08:00.

Because a cancelled queue item never starts, it does not appear as a failed
build in the build history; the cancellation is recorded in the queue and in the
Jenkins log.

## Configuration

In order to use the working hours plugin, you must set up a schedule in your Jenkins
system configuration page. You can configure both daily working hours and specific dates (such
  as holidays). The following configuration will cause jobs with  `enforceBuildSchedule` to queue if ran outside of Monday - Friday 8:00 AM to 6:00 PM.

This page also holds the `Cancel builds scheduled outside working hours` option, which
changes the behaviour from queueing to cancelling — see
[Failing instead of queueing](#failing-instead-of-queueing).

  *Note:* all times are local to your Jenkins controller.

![Configuration options](images/working-hours-config.png "Configuration options")

### Allowable build times
This section contains the times when guarded steps are allowed for each day. Times can be entered in one of the following formats:
- 24 hour eg `09:00`, `18:00`
- Abbreviated 24 hour eg `0900`, `1800`
- 12 hour eg `9:00 AM`, `6:00 PM`

**Note** All times are local to your Jenkins controller.

A build that is queued at a time which is not inside one of the configured ranges for
that day is **held** in the queue until the next allowable time. If
`Cancel builds scheduled outside working hours` is enabled, it is cancelled instead —
see [Failing instead of queueing](#failing-instead-of-queueing). If no time ranges are
configured for a day, builds are held for the whole of that day.

### Excluded dates
This section contains explicit days (such as holidays) on which builds are not allowed
at all. This takes precedence over `Allowable build times`, so builds are always held
(or cancelled, when that option is enabled) on an excluded date. Excluded dates apply
to per-user schedules as well. Here's an example of some holidays configured in the
Working Days calendar:

![Excluded days](images/excluded-days.png "Excluded days")
