# AGENTS.md

## Overview
- This repository is an Atlassian Jira Server/Data Center plugin that adds a `Code` issue tab and Agile issue detail panel backed by stored pull request metadata.
- The plugin also exposes a REST endpoint for creating/updating pull request entries, a user-profile toggle for notification preferences, and an admin screen for global notification/API-user settings.
- Build target is Jira Data Center `11.3.x` (Platform 8: Spring 6, Jakarta EE 10) via AMPS `9.11.x`; **JDK 21 is required** (jira-api 11.x is Java 21 bytecode — JDK 17 fails to compile).

## Tech Stack
- Java plugin packaged as `atlassian-plugin`.
- Dependency injection is done with Atlassian Spring Scanner using `jakarta.inject` annotations (Jira 11's Spring 6 dropped `javax.inject`; do not reintroduce javax.* namespaces anywhere — servlet, ws.rs, xml.bind and inject are all jakarta now).
- REST uses Atlassian REST v2 (`atlassian-rest-v2-api`); security annotations must come from `com.atlassian.plugins.rest.api.security.annotation` or `com.atlassian.annotations.security` — the legacy `com.atlassian.plugins.rest.common.security` package is not recognized on Jira 10+. Writes (`POST`/`DELETE`) are `@LicensedOnly` (platform-enforced authentication) with a matching in-code 401 check; only `GET` is `@UnrestrictedAccess` because reads mirror issue browse visibility.
- Velocity method calls must be declared in the `velocity-allowlist` module in `atlassian-plugin.xml` (enforced since Jira 10.5); when adding a method call to a template, add a matching `<method>` entry. Templates use `$i18n.getText(...)` for user-visible strings (keys in `src/main/resources/i18n/basic-jira-development-panels.properties`, registered as the plugin's `i18n` resource); descriptor labels/tooltips use `key=` attributes against the same bundle.
- Jira auto-HTML-escapes Velocity output by default (since Jira 6.0): text references like `$pullRequest.name` are safe as-is, and adding manual `$textutils.htmlEncode()` would DOUBLE-escape. URL safety is enforced server-side instead (`SafeUrls` + scheme stripping in `mapEntityToModel`), never in templates.
- REST v2 wire behavior: unknown JSON fields are rejected with a 400 by Jackson unless tolerated — `PullRequestModel` carries `@JsonIgnoreProperties(ignoreUnknown = true)` for that reason; `java.util.Date` serializes as epoch milliseconds; Jira 11 disables basic auth by default, so use a Personal Access Token (`Authorization: Bearer ...`) when curl-testing the endpoint. Error responses (400/401/403) carry `{"errorMessages": ["..."]}` bodies; 404/500 are deliberately bodyless.
- Versions of `provided` platform artifacts (SAL, ActiveObjects, atlassian-rest, spring-scanner, jakarta APIs) should track Atlassian's `platform-deps` BOM for the target Jira version (Jira 11.3 = platform-deps 8.3.x) — do not guess them.
- Persistence uses Active Objects (`PullRequestEntity`). Every String column is explicitly `@StringLength(255)` (AO's default, pinned so schema and REST validation cannot drift — `PullRequestResource.MAX_FIELD_LENGTH` must match); `URL` must stay a plain VARCHAR because it is indexed and is the upsert key. The entity is `@Preload` to avoid per-column lazy loads when rendering panels.
- Server-rendered UI uses Velocity templates under `src/main/resources/templates`.
- Tests use JUnit 4 and Mockito; integration tests use `AtlassianPluginsTestRunner`.

## Repository Layout
- `src/main/java/com/alanmosely/jira/plugin/api`: REST model and resource. `PullRequestResource` handles `POST /rest/pullrequest/1.0/code/{issueKey}`.
- `src/main/java/com/alanmosely/jira/plugin/impl`: Core service logic in `PullRequestServiceImpl`.
- `src/main/java/com/alanmosely/jira/plugin/ao`: Active Objects entity definition.
- `src/main/java/com/alanmosely/jira/plugin/tabpanel` and `webpanel`: Issue-view integrations that render stored pull requests.
- `src/main/java/com/alanmosely/jira/plugin/context`, `servlets`, `admin`, `conditions`: Profile toggle, servlet, admin action, and display condition glue code.
- `src/main/java/com/alanmosely/jira/plugin/util`: `SafeUrls` (the single owner of the http(s)-only URL policy used by REST validation, panel rendering and email links — never fork it) and `SettingsKeys` (the SAL settings key constants plus the `codeNotifications` user-property name).
- `src/main/resources/atlassian-plugin.xml`: Plugin module registration. Keep this in sync with any renamed classes, templates, servlet paths, or module keys.
- `src/main/resources/templates`: Velocity views for the tab panel, Agile web panel, user profile panel, and admin page.
- `src/test/java/com/...`: Unit tests, mostly around `PullRequestServiceImpl`.
- `src/test/java/it/...`: Integration tests for the service and AO persistence.

## Core Runtime Behavior
- Pull requests are stored in AO as `PullRequestEntity` records keyed effectively by issue and PR URL. Fields are stored trimmed, with blank optional fields stored as null (a blank field means the same absence as an omitted one, so GET has a single representation); the upsert always rewrites `ISSUE_KEY` to the issue's canonical key (so legacy rows lose their pre-rename key on the next update). A client-supplied `updated` timestamp is honored; otherwise arrival time is used.
- `PullRequestServiceImpl` prefers Jira `issueId` when it can resolve one and falls back to `issueKey` if not. This is important for renamed project keys.
- Retrieval/backfill logic merges newer `ISSUE_ID` rows with legacy `ISSUE_KEY` rows and backfills missing `issueId` values on read. Legacy rows are found via `IssueManager.getAllIssueKeys` (the issue's full key history, which covers project key renames) — never by scanning all `ISSUE_ID IS NULL` rows. Preserve this behavior when touching persistence queries.
- Concurrent POSTs for the same issue + URL can race the find-then-create upsert and leave duplicate rows (AO cannot declare a composite unique constraint). The read path deduplicates by URL keeping the newest row; the upsert self-heals by writing to the newest matching row across ONE combined pool of `ISSUE_ID` rows and legacy rows (the same rule the display uses — `updated` is client-controllable, so upsert target and displayed row MUST agree; a staged bucket-by-bucket search would let the unsearched bucket shadow the target) and deleting older duplicates in the same transaction; `deletePullRequest` deliberately deletes ALL rows matching the URL. URL matching is trim-tolerant everywhere (upsert, legacy rescue, dedupe, delete) because rows written before trim-on-store may hold padded URLs.
- The existence check (`hasPullRequests`) runs on every issue view via the web-panel condition and the tab panel's `showPanel`, so it uses indexed counts only — it deliberately skips the un-indexable `UPPER(ISSUE_KEY)` rescue that the read path still performs. Legacy rows matching only case-insensitively stay hidden until a read or upsert backfills their `ISSUE_ID`; this is an accepted trade-off, do not "fix" it by adding the UPPER count back.
- The issue tab panel intentionally has NO descriptor condition — `PullRequestTabPanel.showPanel` performs the same check, and a condition would double the queries per issue view. The agile web panel keeps its condition (web panels have no showPanel equivalent).
- The REST resource: `POST /rest/pullrequest/1.0/code/{issueKey}` validates payloads (`name` required, `url`/`repoUrl` must be http(s), every field ≤ 255 chars = `MAX_FIELD_LENGTH`, matching the AO schema), and returns 500 when the save fails — `createPullRequest` intentionally propagates persistence exceptions and only swallows notification failures. `GET` returns the stored (sanitized) entries to anyone who can browse the issue, and 500 on a read failure — `getPullRequests` propagates persistence exceptions; the panels catch them themselves. `DELETE ?url=` removes all rows matching that URL under the same authorization as POST, 204/404.
- Global admin settings are stored in SAL plugin settings under the `com.alanmosely.jira.plugin.pullrequestadmin` prefix; the key constants live in `util/SettingsKeys.java` (the two service test classes still use string literals — a deliberate tripwire for accidental key changes).
- User notification opt-in is stored as a Jira user property named `com.alanmosely.jira.plugin.codeNotifications` (`SettingsKeys.CODE_NOTIFICATIONS_USER_PROPERTY` — production code reads the constant).
- REST authorization, in order: (1) writes require an authenticated caller — `@LicensedOnly` plus an in-code 401; (2) the optional configured API user is compared case-insensitively via `Locale.ROOT` lower-casing (NOT `equalsIgnoreCase`, which merges Turkish dotted/dotless i and would let a distinct account impersonate the API user); (3) a browse-permission check on the target issue (missing and invisible issues both return 404 to prevent key probing). Changes here are security-sensitive.
- The admin webwork action is guarded three ways: `roles-required="admin"` in the descriptor, an in-code `GlobalPermissionKey.ADMINISTER` check (websudo is re-authentication, not authorization, and can be disabled instance-wide via `jira.websudo.is.disabled`), and `@WebSudoRequired`. Keep all three.
- Rows whose stored issue key matches the viewed issue but whose non-null `issueId` belongs to a different (since-deleted) issue are deliberately NOT displayed — the pre-2.0 stored-key fallback that surfaced them was showing another issue's pull requests.

## Build And Test
- Preferred local dev commands come from the Atlassian SDK per the README:
  - `atlas-run`
  - `atlas-debug`
  - `atlas-package`
  - `atlas-mvn test`
  - `atlas-mvn integration-test` (KNOWN BROKEN — the README notes this target has never worked here; the ITs under `src/test/java/it/` compile but have no working runner)
- **Set `JAVA_HOME` to a JDK 21 before any `atlas-mvn` command** — the system default java is often older. The failure signature for a wrong JDK is `class file has wrong version 65.0, should be 61.0` on every jira-api class; that means your JDK is too old, not that the dependency is broken.
- CI (`.github/workflows/build.yml`) builds every PR and master push with **plain `mvn` on JDK 21 — no Atlassian SDK**. The pom must therefore stay resolvable without the SDK's settings.xml: that is why `jenkins-releases` is declared in the pom (jira-api transitively needs `commons-httpclient:3.1-jenkins-3`, hosted only there). If CI fails with `Could not find artifact` while `atlas-mvn` works locally, a dependency is leaking in through SDK settings — declare its repository in the pom.
- `atlas-run` shuts down as soon as stdin reaches EOF (AMPS treats it as Ctrl+D), so in a headless or background shell keep stdin open, e.g. `tail -f /dev/null | atlas-run`. The dev instance serves at `http://localhost:2990/jira` (admin/admin) and keeps its home under `target/` between runs; `atlas-clean` resets it.
- Do not use `atlas-integration-test` here; the README explicitly says it runs the wrong product (`refapp`) instead of Jira.
- If you change only Java service logic, unit tests in `src/test/java/com/...` are the first check.
- If you change AO queries, Jira integration points, or plugin wiring, verify against a running instance via `atlas-run` (REST smoke test + panel rendering) — the integration tests have no working runner (see above), so `atlas-run` is the only way those paths actually execute against a real database.
- Unit tests reach Jira state through protected seams overridden by test subclasses: `resolveIssue(String)`/`resolveAllIssueKeys(Issue)` on `PullRequestServiceImpl`, and `loggedInUser()`/`issueByKey(String)`/`canBrowse(Issue, ApplicationUser)` on `PullRequestResource` — extend those seams for new Jira lookups instead of trying to mock `ComponentAccessor` statics.
- Email HTML is built by the package-private statics `buildEmailSubject`/`buildEmailBody`/`escapeHtml`/`linkOrText` on `PullRequestServiceImpl` precisely so `PullRequestEmailContentTest` can pin the escaping — keep them pure (no `ComponentAccessor`).
- Runtime-verified on Jira 11.3.10 via `atlas-run` (2026-09-02, re-verified after the hardening pass): plugin enables; REST handles create/GET/upsert/delete/validation/authz (201/200/204/400/401/403/404 including the over-length 400 that names the field and JSON `errorMessages` bodies); the API-user restriction gates POST/DELETE but not GET, and admin-screen save of it round-trips both ways; the Code tab renders with i18n headers resolved and `<script>`/`<b>` payloads escaped as text; the profile toggle (now a plain submit button) round-trips its XSRF token through the servlet; the admin screen saves via `!save.jspa`, the pre-2.0 URL still renders, and its form carries a non-empty token. Still unverified: the agile board web panel (`atl.gh.issue.details.tab` — needs a Jira Software instance with a board) and actual email delivery (needs a mail server; email HTML escaping is covered by `PullRequestEmailContentTest`).

## Release Process
- Versioning: bump `<version>` in `pom.xml` (plain `X.Y.Z` for production, `X.Y.Z-RC1` for release candidates). Note v2.0.0 is a known-bad release (the as-contributed Jira 11 migration that cannot install); v2.0.1 is the first working 2.x.
- Flow: feature branch -> PR -> merge with a **merge commit** (not squash) -> tag `vX.Y.Z` on the merge commit and push the tag.
- GitHub release: named exactly after the tag, body is a short bullet list of changes ending with `**Full Changelog**: .../compare/vPREV...vNEW`, RC/SNAPSHOT releases are marked prerelease.
- Always attach the built jar (`atlas-mvn clean package` with JDK 21) as `basic-jira-development-panels-X.Y.Z.jar` — every release ships the jar as an asset; build it from the merged code with the pom version already bumped.

## Change Guidance
- Keep plugin descriptor, Spring-scanned components, and Velocity template names aligned. A rename in Java usually requires a matching update in `atlassian-plugin.xml`.
- Preserve the storage keys and property names unless you are intentionally migrating data/settings. Production code reads them from `util/SettingsKeys.java`; the unit and integration tests deliberately duplicate them as literals, so a deliberate key change must touch the tests too.
- When changing pull request persistence, verify all of the following still hold:
  - existing PRs are updated rather than duplicated for the same issue + URL
  - renamed Jira project keys still resolve existing records
  - legacy rows without `issueId` continue to be found and backfilled
- When changing the profile/admin views, keep the expected context keys stable:
  - profile template expects `codeNotifications`, `pluginUrl` (context-relative when a request is executing, so the same-origin XSRF check holds behind proxies) and `atlToken` (the XSRF token the notifications servlet validates; an empty token makes every preference save fail with 403)
  - tab and web panels expect `pullRequests`; nullable model fields must be rendered with quiet references (`$!pullRequest.status`) or they print the literal Velocity reference
  - admin template uses `areNotificationsEnabled()` and `getApiUser()`
  - the profile toggle is a plain form submit button on purpose — do not reintroduce the JS-driven anchor, it broke without JavaScript
- Notification code depends heavily on `ComponentAccessor` and live Jira services. Be conservative about refactors and prefer adding tests around behavior changes.
- HTML in notification emails is assembled manually in Java via the package-private builders in `PullRequestServiceImpl`: every interpolated field must go through `escapeHtml(...)` (a local escaper covering `& < > " '` — apostrophes included, unlike the deprecated commons-lang `escapeHtml4` it replaced) and URLs must go through `linkOrText(...)`; keep attributes double-quoted by convention. Emails are queued after the save transaction commits — keep it that way, the mail queue is not transactional.

## Testing Gaps And Risk Areas
- Unit coverage now spans `PullRequestServiceImpl` (persistence, dedupe, legacy-key pinning), `PullRequestResource` (the full 401/400/403/404/201/204/500 matrix), `SafeUrls`, and the email escaping layer (`PullRequestEmailContentTest`). The servlet, admin action, context provider, condition, and Velocity rendering still have no direct test coverage.
- UI changes in templates should be manually verified in a running Jira instance.
- Security-sensitive areas:
  - `PullRequestResource` access control
  - admin configuration persistence (`PullRequestAdminAction` — keep descriptor `roles-required`, in-code ADMINISTER check and websudo together)
  - user preference writes in `PullRequestNotificationsServlet`

## Practical Workflow For Future Agents
- Start by reading `README.md`, `pom.xml`, and `src/main/resources/atlassian-plugin.xml`.
- For behavior changes, inspect `PullRequestServiceImpl` first; most repo logic funnels through it.
- For UI changes, trace the Java provider/action class and the matching Velocity template together.
- Before editing persistence logic, read the unit test covering issue-key rename/backfill behavior.
- If you add new modules or settings, update tests and document the new key/module in this file or the README.
