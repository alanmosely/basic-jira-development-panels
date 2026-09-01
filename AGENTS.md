# AGENTS.md

## Overview
- This repository is an Atlassian Jira Server/Data Center plugin that adds a `Code` issue tab and Agile issue detail panel backed by stored pull request metadata.
- The plugin also exposes a REST endpoint for creating/updating pull request entries, a user-profile toggle for notification preferences, and an admin screen for global notification/API-user settings.
- Build target is Jira Data Center `11.3.x` (Platform 8: Spring 6, Jakarta EE 10) via AMPS `9.11.x`; **JDK 21 is required** (jira-api 11.x is Java 21 bytecode — JDK 17 fails to compile).

## Tech Stack
- Java plugin packaged as `atlassian-plugin`.
- Dependency injection is done with Atlassian Spring Scanner using `jakarta.inject` annotations (Jira 11's Spring 6 dropped `javax.inject`; do not reintroduce javax.* namespaces anywhere — servlet, ws.rs, xml.bind and inject are all jakarta now).
- REST uses Atlassian REST v2 (`atlassian-rest-v2-api`); security annotations must come from `com.atlassian.plugins.rest.api.security.annotation` or `com.atlassian.annotations.security` — the legacy `com.atlassian.plugins.rest.common.security` package is not recognized on Jira 10+.
- Velocity method calls must be declared in the `velocity-allowlist` module in `atlassian-plugin.xml` (enforced since Jira 10.5); when adding a method call to a template, add a matching `<method>` entry.
- Jira auto-HTML-escapes Velocity output by default (since Jira 6.0): text references like `$pullRequest.name` are safe as-is, and adding manual `$textutils.htmlEncode()` would DOUBLE-escape. URL safety is enforced server-side instead (`SafeUrls` + scheme stripping in `mapEntityToModel`), never in templates.
- REST v2 wire behavior: unknown JSON fields are rejected with a 400 by Jackson unless tolerated — `PullRequestModel` carries `@JsonIgnoreProperties(ignoreUnknown = true)` for that reason; `java.util.Date` serializes as epoch milliseconds; Jira 11 disables basic auth by default, so use a Personal Access Token (`Authorization: Bearer ...`) when curl-testing the endpoint.
- Versions of `provided` platform artifacts (SAL, ActiveObjects, atlassian-rest, spring-scanner, jakarta APIs) should track Atlassian's `platform-deps` BOM for the target Jira version (Jira 11.3 = platform-deps 8.3.x) — do not guess them.
- Persistence uses Active Objects (`PullRequestEntity`).
- Server-rendered UI uses Velocity templates under `src/main/resources/templates`.
- Tests use JUnit 4 and Mockito; integration tests use `AtlassianPluginsTestRunner`.

## Repository Layout
- `src/main/java/com/alanmosely/jira/plugin/api`: REST model and resource. `PullRequestResource` handles `POST /rest/pullrequest/1.0/code/{issueKey}`.
- `src/main/java/com/alanmosely/jira/plugin/impl`: Core service logic in `PullRequestServiceImpl`.
- `src/main/java/com/alanmosely/jira/plugin/ao`: Active Objects entity definition.
- `src/main/java/com/alanmosely/jira/plugin/tabpanel` and `webpanel`: Issue-view integrations that render stored pull requests.
- `src/main/java/com/alanmosely/jira/plugin/context`, `servlets`, `admin`, `conditions`: Profile toggle, servlet, admin action, and display condition glue code.
- `src/main/java/com/alanmosely/jira/plugin/util`: `SafeUrls` (the single owner of the http(s)-only URL policy used by REST validation, panel rendering and email links — never fork it) and `SettingsKeys` (the SAL settings key constants).
- `src/main/resources/atlassian-plugin.xml`: Plugin module registration. Keep this in sync with any renamed classes, templates, servlet paths, or module keys.
- `src/main/resources/templates`: Velocity views for the tab panel, Agile web panel, user profile panel, and admin page.
- `src/test/java/com/...`: Unit tests, mostly around `PullRequestServiceImpl`.
- `src/test/java/it/...`: Integration tests for the service and AO persistence.

## Core Runtime Behavior
- Pull requests are stored in AO as `PullRequestEntity` records keyed effectively by issue and PR URL.
- `PullRequestServiceImpl` prefers Jira `issueId` when it can resolve one and falls back to `issueKey` if not. This is important for renamed project keys.
- Retrieval/backfill logic merges newer `ISSUE_ID` rows with legacy `ISSUE_KEY` rows and backfills missing `issueId` values on read. Legacy rows are found via `IssueManager.getAllIssueKeys` (the issue's full key history, which covers project key renames) — never by scanning all `ISSUE_ID IS NULL` rows. Preserve this behavior when touching persistence queries.
- The REST resource validates payloads (`name` required, `url`/`repoUrl` must be http(s)), requires the caller to have browse permission on a resolvable issue (404 otherwise), and returns 500 when the save fails — `createPullRequest` intentionally propagates persistence exceptions and only swallows notification failures.
- Global admin settings are stored in SAL plugin settings under the `com.alanmosely.jira.plugin.pullrequestadmin` prefix; the key constants live in `util/SettingsKeys.java` (the two test classes still use string literals).
- User notification opt-in is stored as a Jira user property named `com.alanmosely.jira.plugin.codeNotifications`.
- The REST endpoint is marked `@UnrestrictedAccess` (REST v2 annotation); authorization is enforced by (1) the optional configured API user compared case-insensitively against the logged-in username (Jira usernames are unique case-insensitively), and (2) a browse-permission check on the target issue (missing and invisible issues both return 404 to prevent key probing). Changes here are security-sensitive.
- Rows whose stored issue key matches the viewed issue but whose non-null `issueId` belongs to a different (since-deleted) issue are deliberately NOT displayed — the pre-2.0 stored-key fallback that surfaced them was showing another issue's pull requests.

## Build And Test
- Preferred local dev commands come from the Atlassian SDK per the README:
  - `atlas-run`
  - `atlas-debug`
  - `atlas-package`
  - `atlas-mvn test`
  - `atlas-mvn integration-test`
- **Set `JAVA_HOME` to a JDK 21 before any `atlas-mvn` command** — the system default java is often older. The failure signature for a wrong JDK is `class file has wrong version 65.0, should be 61.0` on every jira-api class; that means your JDK is too old, not that the dependency is broken.
- Do not use `atlas-integration-test` here; the README explicitly says it runs the wrong product (`refapp`) instead of Jira.
- If you change only Java service logic, unit tests in `src/test/java/com/...` are the first check.
- If you change AO queries, Jira integration points, or plugin wiring, run integration tests as well.
- Unit tests reach Jira state through the protected seams `resolveIssue(String)` and `resolveAllIssueKeys(Issue)` on `PullRequestServiceImpl` (overridden by a test subclass) — extend those seams for new Jira lookups instead of trying to mock `ComponentAccessor` statics.
- Known gap (as of Sep 2026): the 2.x line has never been smoke-tested with `atlas-run` against Jira 11.3 — plugin enablement, a live POST, and the four screens rendering are unverified at runtime. Remove this note once that has been done.

## Release Process
- Versioning: bump `<version>` in `pom.xml` (plain `X.Y.Z` for production, `X.Y.Z-RC1` for release candidates). Note v2.0.0 is a known-bad release (the as-contributed Jira 11 migration that cannot install); v2.0.1 is the first working 2.x.
- Flow: feature branch -> PR -> merge with a **merge commit** (not squash) -> tag `vX.Y.Z` on the merge commit and push the tag.
- GitHub release: named exactly after the tag, body is a short bullet list of changes ending with `**Full Changelog**: .../compare/vPREV...vNEW`, RC/SNAPSHOT releases are marked prerelease.
- Always attach the built jar (`atlas-mvn clean package` with JDK 21) as `basic-jira-development-panels-X.Y.Z.jar` — every release ships the jar as an asset; build it from the merged code with the pom version already bumped.

## Change Guidance
- Keep plugin descriptor, Spring-scanned components, and Velocity template names aligned. A rename in Java usually requires a matching update in `atlassian-plugin.xml`.
- Preserve the storage keys and property names unless you are intentionally migrating data/settings. Production code reads them from `util/SettingsKeys.java`; the unit and integration tests still duplicate them as literals, so a deliberate key change must touch the tests too.
- When changing pull request persistence, verify all of the following still hold:
  - existing PRs are updated rather than duplicated for the same issue + URL
  - renamed Jira project keys still resolve existing records
  - legacy rows without `issueId` continue to be found and backfilled
- When changing the profile/admin views, keep the expected context keys stable:
  - profile template expects `codeNotifications`, `pluginUrl` and `atlToken` (the XSRF token the notifications servlet validates; an empty token makes every preference save fail with 403)
  - tab and web panels expect `pullRequests`
  - admin template uses `areNotificationsEnabled()` and `getApiUser()`
- Notification code depends heavily on `ComponentAccessor` and live Jira services. Be conservative about refactors and prefer adding tests around behavior changes.
- HTML in notification emails is assembled manually in Java, but an escaping layer now exists in `PullRequestServiceImpl`: every interpolated field must go through `escapeHtml(...)`, URLs must go through `linkOrText(...)`, and HTML attributes must stay DOUBLE-quoted (`escapeHtml4` does not escape apostrophes, so single-quoted attributes are injectable). Emails are queued after the save transaction commits — keep it that way, the mail queue is not transactional.

## Testing Gaps And Risk Areas
- Most automated coverage is in `PullRequestServiceImpl`; servlet, admin action, context provider, condition, and Velocity rendering have little or no direct test coverage.
- UI changes in templates should be manually verified in a running Jira instance.
- Security-sensitive areas:
  - `PullRequestResource` access control
  - admin configuration persistence
  - user preference writes in `PullRequestNotificationsServlet`

## Practical Workflow For Future Agents
- Start by reading `README.md`, `pom.xml`, and `src/main/resources/atlassian-plugin.xml`.
- For behavior changes, inspect `PullRequestServiceImpl` first; most repo logic funnels through it.
- For UI changes, trace the Java provider/action class and the matching Velocity template together.
- Before editing persistence logic, read the unit test covering issue-key rename/backfill behavior.
- If you add new modules or settings, update tests and document the new key/module in this file or the README.
