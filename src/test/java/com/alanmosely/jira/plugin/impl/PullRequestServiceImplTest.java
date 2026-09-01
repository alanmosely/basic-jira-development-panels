package com.alanmosely.jira.plugin.impl;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.issue.Issue;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;

import net.java.ao.Query;

public class PullRequestServiceImplTest {

    private ActiveObjects activeObjects;
    private PluginSettingsFactory pluginSettingsFactory;
    private PluginSettings pluginSettings;
    private TransactionTemplate transactionTemplate;

    private static final String PLUGIN_STORAGE_KEY = "com.alanmosely.jira.plugin.pullrequestadmin";

    private static class TestPullRequestServiceImpl extends PullRequestServiceImpl {
        private final Issue issue;
        private final Set<String> allKeys;

        TestPullRequestServiceImpl(ActiveObjects activeObjects, PluginSettingsFactory pluginSettingsFactory,
                TransactionTemplate transactionTemplate, Issue issue, Set<String> allKeys) {
            super(activeObjects, pluginSettingsFactory, transactionTemplate);
            this.issue = issue;
            this.allKeys = allKeys;
        }

        @Override
        protected Issue resolveIssue(String issueKey) {
            return issue;
        }

        @Override
        protected Set<String> resolveAllIssueKeys(Issue issue) {
            return allKeys;
        }
    }

    @Before
    public void setUp() {
        activeObjects = mock(ActiveObjects.class);
        pluginSettingsFactory = mock(PluginSettingsFactory.class);
        pluginSettings = mock(PluginSettings.class);
        transactionTemplate = mock(TransactionTemplate.class);

        when(pluginSettingsFactory.createGlobalSettings()).thenReturn(pluginSettings);
        when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction());
    }

    private static Issue mockIssue(Long issueId, String issueKey) {
        Issue issue = mock(Issue.class);
        when(issue.getId()).thenReturn(issueId);
        when(issue.getKey()).thenReturn(issueKey);
        return issue;
    }

    private static PullRequestModel model() {
        PullRequestModel model = new PullRequestModel();
        model.setName("PR Name");
        model.setUrl("http://example.com/pr/1");
        model.setStatus("Open");
        model.setRepoName("Repo Name");
        model.setRepoUrl("http://example.com/repo");
        model.setBranchName("feature-branch");
        model.setUpdated(new Date());
        return model;
    }

    private PullRequestServiceImpl serviceFor(Issue issue, Set<String> allKeys) {
        return new TestPullRequestServiceImpl(activeObjects, pluginSettingsFactory, transactionTemplate,
                issue, allKeys);
    }

    @Test
    public void testCreatePullRequest_WhenProcessingEnabled() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("true");

        PullRequestModel model = model();
        Issue issue = mockIssue(1001L, "TEST-1");

        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(entity);

        PullRequestServiceImpl spyService = spy(serviceFor(issue, Collections.emptySet()));
        doNothing().when(spyService).sendNotifications(any(PullRequestEntity.class));

        spyService.createPullRequest("TEST-1", model);

        verify(entity).setIssueKey("TEST-1");
        verify(entity).setIssueId(1001L);
        verify(entity).setName(model.getName());
        verify(entity).setUrl(model.getUrl());
        verify(entity).setStatus(model.getStatus());
        verify(entity).setRepoName(model.getRepoName());
        verify(entity).setRepoUrl(model.getRepoUrl());
        verify(entity).setBranchName(model.getBranchName());
        verify(entity).setUpdated(any(Date.class));
        verify(entity).save();

        verify(spyService).sendNotifications(entity);
    }

    @Test
    public void testCreatePullRequest_WhenProcessingDisabled() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        PullRequestModel model = model();
        Issue issue = mockIssue(1001L, "TEST-1");

        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(entity);

        PullRequestServiceImpl spyService = spy(serviceFor(issue, Collections.emptySet()));
        doNothing().when(spyService).sendNotifications(any(PullRequestEntity.class));

        spyService.createPullRequest("TEST-1", model);

        verify(entity).save();
        verify(spyService, never()).sendNotifications(any(PullRequestEntity.class));
    }

    @Test
    public void testCreatePullRequest_UsesCanonicalKeyCasing() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        Issue issue = mockIssue(1001L, "TEST-1");

        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(entity);

        serviceFor(issue, Collections.emptySet()).createPullRequest("test-1", model());

        verify(entity).setIssueKey("TEST-1");
    }

    @Test
    public void testCreatePullRequest_UpdatesLegacyRowInsteadOfDuplicating() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        PullRequestModel model = model();
        Issue issue = mockIssue(1001L, "BAR-1");

        // Pre-upgrade row: same PR URL, stored under the pre-rename key, no issue id.
        PullRequestEntity legacyEntity = mock(PullRequestEntity.class);
        when(legacyEntity.getIssueId()).thenReturn(null);
        when(legacyEntity.getUrl()).thenReturn(model.getUrl());

        // First find: ISSUE_ID + URL (empty); second find: legacy historical-key query.
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] {}, new PullRequestEntity[] { legacyEntity });

        serviceFor(issue, new HashSet<>(Collections.singletonList("FOO-1")))
                .createPullRequest("BAR-1", model);

        verify(activeObjects, never()).create(PullRequestEntity.class);
        verify(legacyEntity).setIssueId(1001L);
        verify(legacyEntity).setName(model.getName());
    }

    @Test(expected = IllegalStateException.class)
    public void testCreatePullRequest_PropagatesPersistenceFailures() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        Issue issue = mockIssue(1001L, "TEST-1");
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenThrow(new IllegalStateException("db down"));

        serviceFor(issue, Collections.emptySet()).createPullRequest("TEST-1", model());
    }

    @Test
    public void testCreatePullRequest_NotificationFailureDoesNotFailSave() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("true");

        Issue issue = mockIssue(1001L, "TEST-1");
        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(entity);

        PullRequestServiceImpl spyService = spy(serviceFor(issue, Collections.emptySet()));
        doThrow(new IllegalStateException("mail down")).when(spyService)
                .sendNotifications(any(PullRequestEntity.class));

        spyService.createPullRequest("TEST-1", model());

        verify(entity).save();
    }

    @Test
    public void testGetPullRequests_ByStoredKeyWhenIssueDoesNotResolve() {
        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(entity.getName()).thenReturn("PR Name");
        when(entity.getUrl()).thenReturn("http://example.com/pr/1");
        when(entity.getStatus()).thenReturn("Open");
        when(entity.getRepoName()).thenReturn("Repo Name");
        when(entity.getRepoUrl()).thenReturn("http://example.com/repo");
        when(entity.getBranchName()).thenReturn("feature-branch");
        when(entity.getUpdated()).thenReturn(new Date());

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { entity });

        List<PullRequestModel> models = serviceFor(null, Collections.emptySet()).getPullRequests("TEST-1");

        assertNotNull(models);
        assertEquals(1, models.size());
        PullRequestModel model = models.get(0);
        assertEquals("PR Name", model.getName());
        assertEquals("http://example.com/pr/1", model.getUrl());
        assertEquals("Open", model.getStatus());
        assertEquals("Repo Name", model.getRepoName());
        assertEquals("http://example.com/repo", model.getRepoUrl());
        assertEquals("feature-branch", model.getBranchName());
        assertNotNull(model.getUpdated());
    }

    @Test
    public void testGetPullRequests_StripsUnsafeUrlSchemes() {
        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(entity.getName()).thenReturn("PR Name");
        when(entity.getUrl()).thenReturn("javascript:alert(1)");
        when(entity.getRepoUrl()).thenReturn("http://example.com/repo");
        when(entity.getUpdated()).thenReturn(new Date());

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { entity });

        List<PullRequestModel> models = serviceFor(null, Collections.emptySet()).getPullRequests("TEST-1");

        assertEquals(1, models.size());
        assertNull(models.get(0).getUrl());
        assertEquals("http://example.com/repo", models.get(0).getRepoUrl());
    }

    @Test
    public void testHasPullRequests_ByIssueId() {
        Issue issue = mockIssue(1001L, "TEST-1");
        when(activeObjects.count(eq(PullRequestEntity.class), any(Query.class))).thenReturn(1);

        assertTrue(serviceFor(issue, Collections.emptySet()).hasPullRequests("TEST-1"));
    }

    @Test
    public void testHasPullRequests_FindsLegacyRowsWhenNoneMatchById() {
        Issue issue = mockIssue(1001L, "BAR-1");
        // First count: ISSUE_ID query (0); second count: legacy exact-key query (1).
        when(activeObjects.count(eq(PullRequestEntity.class), any(Query.class))).thenReturn(0, 1);

        assertTrue(serviceFor(issue, new HashSet<>(Collections.singletonList("FOO-1")))
                .hasPullRequests("BAR-1"));
    }

    @Test
    public void testGetPullRequestsBackfillsIssueIdForRenamedProjectKey() {
        Long issueId = 1001L;
        Issue issue = mockIssue(issueId, "BAR-1");

        PullRequestEntity legacyEntity = mock(PullRequestEntity.class);
        when(legacyEntity.getID()).thenReturn(7);
        when(legacyEntity.getIssueId()).thenReturn(null);
        when(legacyEntity.getIssueKey()).thenReturn("FOO-1");
        when(legacyEntity.getName()).thenReturn("PR Name");
        when(legacyEntity.getUrl()).thenReturn("http://example.com/pr/1");
        when(legacyEntity.getUpdated()).thenReturn(new Date());

        // First find: ISSUE_ID query (empty); second find: legacy historical-key query.
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] {}, new PullRequestEntity[] { legacyEntity });

        PullRequestServiceImpl service = serviceFor(issue, new HashSet<>(Collections.singletonList("FOO-1")));
        List<PullRequestModel> models = service.getPullRequests("BAR-1");

        assertNotNull(models);
        assertEquals(1, models.size());
        verify(legacyEntity).setIssueId(issueId);
        verify(legacyEntity).save();
    }

    @Test
    public void testGetPullRequestsToleratesNullUpdatedOnLegacyRows() {
        Long issueId = 1001L;
        Issue issue = mockIssue(issueId, "BAR-1");

        PullRequestEntity currentEntity = mock(PullRequestEntity.class);
        when(currentEntity.getID()).thenReturn(1);
        when(currentEntity.getIssueId()).thenReturn(issueId);
        when(currentEntity.getName()).thenReturn("Newer PR");
        when(currentEntity.getUpdated()).thenReturn(new Date());

        // Rows created before the UPDATED column existed have a null date; the merge
        // sort must not throw and must put them last.
        PullRequestEntity legacyEntity = mock(PullRequestEntity.class);
        when(legacyEntity.getID()).thenReturn(2);
        when(legacyEntity.getIssueId()).thenReturn(null);
        when(legacyEntity.getIssueKey()).thenReturn("FOO-1");
        when(legacyEntity.getName()).thenReturn("Ancient PR");
        when(legacyEntity.getUpdated()).thenReturn(null);

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { currentEntity }, new PullRequestEntity[] { legacyEntity });

        List<PullRequestModel> models = serviceFor(issue, new HashSet<>(Collections.singletonList("FOO-1")))
                .getPullRequests("BAR-1");

        assertEquals(2, models.size());
        assertEquals("Newer PR", models.get(0).getName());
        assertEquals("Ancient PR", models.get(1).getName());
    }

    @Test
    public void testGetPullRequestsDoesNotDuplicateBackfilledRows() {
        Long issueId = 1001L;
        Issue issue = mockIssue(issueId, "BAR-1");

        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(entity.getID()).thenReturn(1);
        when(entity.getIssueId()).thenReturn(issueId);
        when(entity.getName()).thenReturn("PR Name");
        when(entity.getUpdated()).thenReturn(new Date());

        // Same row returned by both the ISSUE_ID query and the legacy query.
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { entity }, new PullRequestEntity[] { entity });

        List<PullRequestModel> models = serviceFor(issue, new HashSet<>(Collections.singletonList("FOO-1")))
                .getPullRequests("BAR-1");

        assertEquals(1, models.size());
    }
}
