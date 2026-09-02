package com.alanmosely.jira.plugin.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

/**
 * Pins the authorization/validation matrix of the REST resource (401/400/403/404/
 * 201/204/500) using the protected Jira-lookup seams, mirroring the seam pattern
 * used to test PullRequestServiceImpl.
 */
public class PullRequestResourceTest {

    private static final String API_USER_KEY = "com.alanmosely.jira.plugin.pullrequestadmin.apiUser";

    private PullRequestService pullRequestService;
    private PluginSettingsFactory pluginSettingsFactory;
    private PluginSettings pluginSettings;

    private class TestResource extends PullRequestResource {
        private final ApplicationUser currentUser;
        private final Issue resolvedIssue;
        private final boolean browsable;

        TestResource(ApplicationUser currentUser, Issue resolvedIssue, boolean browsable) {
            super(pullRequestService, pluginSettingsFactory);
            this.currentUser = currentUser;
            this.resolvedIssue = resolvedIssue;
            this.browsable = browsable;
        }

        @Override
        protected ApplicationUser loggedInUser() {
            return currentUser;
        }

        @Override
        protected Issue issueByKey(String issueKey) {
            return resolvedIssue;
        }

        @Override
        protected boolean canBrowse(Issue issue, ApplicationUser user) {
            return browsable;
        }
    }

    @Before
    public void setUp() {
        pullRequestService = mock(PullRequestService.class);
        pluginSettingsFactory = mock(PluginSettingsFactory.class);
        pluginSettings = mock(PluginSettings.class);
        when(pluginSettingsFactory.createGlobalSettings()).thenReturn(pluginSettings);
    }

    private static ApplicationUser user(String username) {
        ApplicationUser user = mock(ApplicationUser.class);
        when(user.getUsername()).thenReturn(username);
        return user;
    }

    private static Issue issue(String key) {
        Issue issue = mock(Issue.class);
        when(issue.getKey()).thenReturn(key);
        return issue;
    }

    private static PullRequestModel model() {
        PullRequestModel model = new PullRequestModel();
        model.setName("PR Name");
        model.setUrl("https://example.com/pr/1");
        model.setStatus("Open");
        model.setRepoName("Repo");
        model.setRepoUrl("https://example.com/repo");
        model.setBranchName("feature");
        return model;
    }

    @SuppressWarnings("unchecked")
    private static String firstErrorMessage(Response response) {
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        return ((List<String>) entity.get("errorMessages")).get(0);
    }

    @Test
    public void post_RequiresAuthentication() {
        Response response = new TestResource(null, issue("TEST-1"), true).addPullRequest("TEST-1", model());

        assertEquals(401, response.getStatus());
        verify(pullRequestService, never()).createPullRequest(any(Issue.class), any(PullRequestModel.class));
    }

    @Test
    public void post_RejectsMissingBody() {
        Response response = new TestResource(user("bot"), issue("TEST-1"), true).addPullRequest("TEST-1", null);

        assertEquals(400, response.getStatus());
    }

    @Test
    public void post_RejectsBlankName() {
        PullRequestModel model = model();
        model.setName("   ");

        Response response = new TestResource(user("bot"), issue("TEST-1"), true).addPullRequest("TEST-1", model);

        assertEquals(400, response.getStatus());
        assertTrue(firstErrorMessage(response).contains("name"));
    }

    @Test
    public void post_RejectsNonHttpUrl() {
        PullRequestModel model = model();
        model.setUrl("javascript:alert(1)");

        Response response = new TestResource(user("bot"), issue("TEST-1"), true).addPullRequest("TEST-1", model);

        assertEquals(400, response.getStatus());
        assertTrue(firstErrorMessage(response).contains("url"));
    }

    @Test
    public void post_RejectsNonHttpRepoUrl() {
        PullRequestModel model = model();
        model.setRepoUrl("ftp://example.com/repo");

        Response response = new TestResource(user("bot"), issue("TEST-1"), true).addPullRequest("TEST-1", model);

        assertEquals(400, response.getStatus());
        assertTrue(firstErrorMessage(response).contains("repoUrl"));
    }

    @Test
    public void post_RejectsOverLengthFieldsNamingTheField() {
        PullRequestModel model = model();
        model.setBranchName("b".repeat(PullRequestResource.MAX_FIELD_LENGTH + 1));

        Response response = new TestResource(user("bot"), issue("TEST-1"), true).addPullRequest("TEST-1", model);

        assertEquals(400, response.getStatus());
        assertTrue(firstErrorMessage(response).contains("branchName"));
        verify(pullRequestService, never()).createPullRequest(any(Issue.class), any(PullRequestModel.class));
    }

    @Test
    public void post_RejectsCallerOtherThanConfiguredApiUser() {
        when(pluginSettings.get(API_USER_KEY)).thenReturn("ci-bot");

        Response response = new TestResource(user("intruder"), issue("TEST-1"), true).addPullRequest("TEST-1",
                model());

        assertEquals(403, response.getStatus());
        verify(pullRequestService, never()).createPullRequest(any(Issue.class), any(PullRequestModel.class));
    }

    @Test
    public void post_MatchesConfiguredApiUserCaseInsensitively() {
        when(pluginSettings.get(API_USER_KEY)).thenReturn(" CI-Bot ");
        Issue issue = issue("TEST-1");

        Response response = new TestResource(user("ci-bot"), issue, true).addPullRequest("TEST-1", model());

        assertEquals(201, response.getStatus());
        verify(pullRequestService).createPullRequest(any(Issue.class), any(PullRequestModel.class));
    }

    @Test
    public void post_ReturnsNotFoundForUnresolvableIssue() {
        Response response = new TestResource(user("bot"), null, true).addPullRequest("NOPE-1", model());

        assertEquals(404, response.getStatus());
    }

    @Test
    public void post_ReturnsNotFoundForUnbrowsableIssue() {
        Response response = new TestResource(user("bot"), issue("TEST-1"), false).addPullRequest("TEST-1", model());

        assertEquals(404, response.getStatus());
        verify(pullRequestService, never()).createPullRequest(any(Issue.class), any(PullRequestModel.class));
    }

    @Test
    public void post_CreatesTrimsAllFieldsAndTreatsBlanksAsOmitted() {
        PullRequestModel model = model();
        model.setName("  PR Name  ");
        model.setUrl("  https://example.com/pr/1  ");
        model.setStatus("   "); // blank == omitted: must reach the service as null
        model.setRepoName("  Repo  ");
        model.setRepoUrl("  https://example.com/repo  ");
        model.setBranchName("  feature  ");
        Issue issue = issue("TEST-1");

        Response response = new TestResource(user("bot"), issue, true).addPullRequest("TEST-1", model);

        assertEquals(201, response.getStatus());
        assertEquals("PR Name", model.getName());
        assertEquals("https://example.com/pr/1", model.getUrl());
        assertNull(model.getStatus());
        assertEquals("Repo", model.getRepoName());
        assertEquals("https://example.com/repo", model.getRepoUrl());
        assertEquals("feature", model.getBranchName());
        verify(pullRequestService).createPullRequest(issue, model);
    }

    @Test
    public void post_ReturnsServerErrorWhenSaveFails() {
        doThrow(new IllegalStateException("db down")).when(pullRequestService)
                .createPullRequest(any(Issue.class), any(PullRequestModel.class));

        Response response = new TestResource(user("bot"), issue("TEST-1"), true).addPullRequest("TEST-1", model());

        assertEquals(500, response.getStatus());
    }

    @Test
    public void get_ReturnsNotFoundForUnbrowsableIssue() {
        Response response = new TestResource(user("bot"), issue("TEST-1"), false).getPullRequests("TEST-1");

        assertEquals(404, response.getStatus());
    }

    @Test
    public void post_ChecksApiUserBeforeIssueLookup() {
        when(pluginSettings.get(API_USER_KEY)).thenReturn("ci-bot");

        // Even for an unresolvable issue the API-user gate must answer first, so
        // non-API callers never reach the issue lookup at all.
        Response response = new TestResource(user("intruder"), null, true).addPullRequest("NOPE-1", model());

        assertEquals(403, response.getStatus());
    }

    @Test
    public void get_ReturnsNotFoundForUnresolvableIssue() {
        Response response = new TestResource(user("bot"), null, true).getPullRequests("NOPE-1");

        assertEquals(404, response.getStatus());
    }

    @Test
    public void get_ReturnsServerErrorWhenReadFails() {
        Issue issue = issue("TEST-1");
        when(pullRequestService.getPullRequests(issue)).thenThrow(new IllegalStateException("db down"));

        // A read failure must be a 500, never a misleading 200 with an empty list.
        Response response = new TestResource(user("bot"), issue, true).getPullRequests("TEST-1");

        assertEquals(500, response.getStatus());
    }

    @Test
    public void get_IsNotGatedByConfiguredApiUser() {
        // Documented asymmetry: the API-user restriction gates writes only; reads
        // mirror issue browse visibility.
        when(pluginSettings.get(API_USER_KEY)).thenReturn("ci-bot");
        Issue issue = issue("TEST-1");
        when(pullRequestService.getPullRequests(issue)).thenReturn(Collections.emptyList());

        Response response = new TestResource(user("someone-else"), issue, true).getPullRequests("TEST-1");

        assertEquals(200, response.getStatus());
    }

    @Test
    public void get_ReturnsStoredPullRequests() {
        Issue issue = issue("TEST-1");
        List<PullRequestModel> stored = Collections.singletonList(model());
        when(pullRequestService.getPullRequests(issue)).thenReturn(stored);

        Response response = new TestResource(null, issue, true).getPullRequests("TEST-1");

        assertEquals(200, response.getStatus());
        assertEquals(stored, response.getEntity());
    }

    @Test
    public void delete_RequiresAuthentication() {
        Response response = new TestResource(null, issue("TEST-1"), true).deletePullRequest("TEST-1",
                "https://example.com/pr/1");

        assertEquals(401, response.getStatus());
        verify(pullRequestService, never()).deletePullRequest(any(Issue.class), any(String.class));
    }

    @Test
    public void delete_RequiresUrlParameter() {
        Response response = new TestResource(user("bot"), issue("TEST-1"), true).deletePullRequest("TEST-1", " ");

        assertEquals(400, response.getStatus());
    }

    @Test
    public void delete_RejectsCallerOtherThanConfiguredApiUser() {
        when(pluginSettings.get(API_USER_KEY)).thenReturn("ci-bot");

        Response response = new TestResource(user("intruder"), issue("TEST-1"), true).deletePullRequest("TEST-1",
                "https://example.com/pr/1");

        assertEquals(403, response.getStatus());
        verify(pullRequestService, never()).deletePullRequest(any(Issue.class), any(String.class));
    }

    @Test
    public void delete_ChecksApiUserBeforeIssueLookup() {
        when(pluginSettings.get(API_USER_KEY)).thenReturn("ci-bot");

        Response response = new TestResource(user("intruder"), null, true).deletePullRequest("NOPE-1",
                "https://example.com/pr/1");

        assertEquals(403, response.getStatus());
        verify(pullRequestService, never()).deletePullRequest(any(Issue.class), any(String.class));
    }

    @Test
    public void delete_ReturnsNotFoundForUnresolvableIssue() {
        Response response = new TestResource(user("bot"), null, true).deletePullRequest("NOPE-1",
                "https://example.com/pr/1");

        assertEquals(404, response.getStatus());
        verify(pullRequestService, never()).deletePullRequest(any(Issue.class), any(String.class));
    }

    @Test
    public void delete_ReturnsNotFoundForUnbrowsableIssue() {
        Response response = new TestResource(user("bot"), issue("TEST-1"), false).deletePullRequest("TEST-1",
                "https://example.com/pr/1");

        assertEquals(404, response.getStatus());
        verify(pullRequestService, never()).deletePullRequest(any(Issue.class), any(String.class));
    }

    @Test
    public void delete_ReturnsNotFoundWhenNothingMatches() {
        Issue issue = issue("TEST-1");
        when(pullRequestService.deletePullRequest(issue, "https://example.com/pr/1")).thenReturn(false);

        Response response = new TestResource(user("bot"), issue, true).deletePullRequest("TEST-1",
                "https://example.com/pr/1");

        assertEquals(404, response.getStatus());
    }

    @Test
    public void delete_RemovesMatchingPullRequest() {
        Issue issue = issue("TEST-1");
        when(pullRequestService.deletePullRequest(issue, "https://example.com/pr/1")).thenReturn(true);

        Response response = new TestResource(user("bot"), issue, true).deletePullRequest("TEST-1",
                "https://example.com/pr/1");

        assertEquals(204, response.getStatus());
    }

    @Test
    public void delete_ReturnsServerErrorWhenServiceFails() {
        Issue issue = issue("TEST-1");
        when(pullRequestService.deletePullRequest(issue, "https://example.com/pr/1"))
                .thenThrow(new IllegalStateException("db down"));

        Response response = new TestResource(user("bot"), issue, true).deletePullRequest("TEST-1",
                "https://example.com/pr/1");

        assertEquals(500, response.getStatus());
    }
}
