# Basic Jira Development Panels Plugin

The Basic Jira Development Panels plugin adds a new 'Code' tab to the Jira issue view. It exposes an API endpoint that allows you to create and update Pull Request entries that are shown in this 'Code' tab on Jira issues.

If you can connect your source code management tool to Jira, you should totally use the [out of the box Development Tools functionality](https://confluence.atlassian.com/jirasoftwareserver/configuring-development-tools-938845350.html). This plugin was written for organisations where directly connecting to a source code management tool is not desired or possible.

In the future, we may add a 'Builds' tab that will allow the creation and updating of Build entries.

## Usage

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

## Development

This plugin is built using the [Atlassian Plugin SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/set-up-the-atlassian-plugin-sdk-and-build-a-project/) and **requires JDK 11** to build and run.

Once you have installed the SDK you can use the following commands:

* atlas-run   -- installs this plugin into the product and starts it on localhost
* atlas-debug -- same as atlas-run, but allows a debugger to attach at port 5005
* atlas-package -- creates a jar file, which is used by the product during plugin installation
* atlas-mvn integration-test -- runs the integration tests for the plugin  (do not use atlas-integration-test as it runs refapp, not jira)
* atlas-clean -- clears the previous version of the host application from your build output directory
* atlas-help  -- prints description for all commands in the SDK

Full documentation is available at:

<https://developer.atlassian.com/display/DOCS/Introduction+to+the+Atlassian+Plugin+SDK>
