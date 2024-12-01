# Basic Jira Development Panels Plugin

The Basic Jira Development Panels plugin adds a new 'Code' tab to the Jira issue view. It exposes an API endpoint that allows you to create and update Pull Request entries that are shown in this 'Code' tab on Jira issues.

If you can connect your source code management tool to Jira, you should totally use the [out of the box Development Tools functionality](https://confluence.atlassian.com/jirasoftwareserver/configuring-development-tools-938845350.html). This plugin was written for organisations where directly connecting to a source code management tool is not desired or possible.

In the future, we may add a 'Builds' tab that will allow the creation and updating of Build entries.

## Usage

### Viewing Pull Request information

When the plugin is installed and enabled and at least one Pull Request has been associated with a Jira issue, a new 'Code' tab will appear in the 'Activity' panel at the bottom of the Jira issue view. This tab will show a list of all Pull Requests associated with the issue.

![jira-issue](https://github.com/user-attachments/assets/df3efe08-aecd-4b11-bc85-7563f7aef659)

### Configuring email notifications

No email notifications are sent when a Pull Request is created or updated by default. Users can individually opt into email notifications in their User Profiles by selecting 'Enable Code notifications'. When a user has this option enabled, if they are the issue assignee, reporter or a watcher when a Pull Request is created or updated, they will receive an email notification.

![jira-profile](https://github.com/user-attachments/assets/ebbac1f5-1ee9-4e66-9df0-2c52bbab0b23)

### Creating/updating Pull Requests against Jira issues

To create/update a Pull Request entry against the `FOO-1` issue on a Jira instance @ <http://localhost:2990/jira>, you can use the following `curl` command (the Jira user does not require any special permissions):

```bash
curl -X POST -H "Content-Type: application/json" \
     -u "admin:admin" \
     -d '{
       "name": "My PR Name",
       "url": "https://github.com/repo/pull/123",
       "status": "Open",
       "branchName": "my-branch",
       "repoName": "MyRepo",
       "repoUrl": "https://github.com/repo"
     }' http://localhost:2990/jira/rest/pullrequest/1.0/code/FOO-1
```

## Administration

Once the plugin is installed, you can configure the following settings in the Jira administration panel (under Administration > System > Mail > Code Notifications):

* **Enable Email Notifications**: This enables the processing and sending of email notifications when a Pull Request is created or updated (for users that have opted in)
* **Restrict API to User**: This allows you to restrict the API endpoint to a specific user. This is useful if you want to limit who can create or update Pull Requests

![jira-admin](https://github.com/user-attachments/assets/d3559742-3e5b-46d4-866b-f5dfbd59304f)

## Development

This plugin is built using the [Atlassian Plugin SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/set-up-the-atlassian-plugin-sdk-and-build-a-project/) and **requires JDK 11** to build and run.

Once you have installed the SDK you can use the following commands:

* atlas-run   -- installs this plugin into the product and starts it on localhost
* atlas-debug -- same as atlas-run, but allows a debugger to attach at port 5005
* atlas-package -- creates a jar file, which is used by the product during plugin installation
* atlas-mvn test -- runs the unit tests for the plugin
* atlas-mvn integration-test -- runs the integration tests for the plugin  (do not use atlas-integration-test as it runs refapp, not jira) - have not been able to make this work yet
* atlas-clean -- clears the previous version of the host application from your build output directory
* atlas-help  -- prints description for all commands in the SDK

Full documentation is available at:

<https://developer.atlassian.com/display/DOCS/Introduction+to+the+Atlassian+Plugin+SDK>
