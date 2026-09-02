package com.alanmosely.jira.plugin.impl;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InOrder;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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
    public void testCreatePullRequest_HonorsClientUpdatedTimestamp() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        Date clientTimestamp = new Date(1_600_000_000_000L);
        PullRequestModel model = model();
        model.setUpdated(clientTimestamp);
        Issue issue = mockIssue(1001L, "TEST-1");

        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(entity);

        serviceFor(issue, Collections.emptySet()).createPullRequest("TEST-1", model);

        verify(entity).setUpdated(clientTimestamp);
    }

    @Test
    public void testCreatePullRequest_TrimsAllStoredFieldsAndStoresBlanksAsNull() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        PullRequestModel model = model();
        model.setName("  PR Name  ");
        model.setUrl("  http://example.com/pr/1  ");
        model.setStatus("   "); // blank == omitted: must be stored as null, not ""
        model.setRepoName("  Repo Name  ");
        model.setRepoUrl("  http://example.com/repo  ");
        model.setBranchName("  feature-branch  ");
        Issue issue = mockIssue(1001L, "TEST-1");

        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(entity);

        serviceFor(issue, Collections.emptySet()).createPullRequest("TEST-1", model);

        verify(entity).setName("PR Name");
        verify(entity).setUrl("http://example.com/pr/1");
        verify(entity).setStatus(null);
        verify(entity).setRepoName("Repo Name");
        verify(entity).setRepoUrl("http://example.com/repo");
        verify(entity).setBranchName("feature-branch");
    }

    @Test
    public void testCreatePullRequest_QueuesNotificationsOnlyAfterSave() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("true");

        Issue issue = mockIssue(1001L, "TEST-1");
        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(entity);

        PullRequestServiceImpl spyService = spy(serviceFor(issue, Collections.emptySet()));
        doNothing().when(spyService).sendNotifications(any(PullRequestEntity.class));

        spyService.createPullRequest("TEST-1", model());

        // The mail queue is not transactional: the email may only be queued after
        // the save. Pin the ordering, not just that both calls happened.
        InOrder inOrder = inOrder(entity, spyService);
        inOrder.verify(entity).save();
        inOrder.verify(spyService).sendNotifications(entity);
    }

    @Test
    public void testCreatePullRequest_SkipsNotificationsWhenSaveFails() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("true");

        Issue issue = mockIssue(1001L, "TEST-1");
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenThrow(new IllegalStateException("db down"));

        PullRequestServiceImpl spyService = spy(serviceFor(issue, Collections.emptySet()));

        try {
            spyService.createPullRequest("TEST-1", model());
            fail("expected the persistence failure to propagate");
        } catch (IllegalStateException expected) {
            // propagated by design
        }
        verify(spyService, never()).sendNotifications(any(PullRequestEntity.class));
    }

    @Test
    public void testCreatePullRequest_RefreshesStaleKeyOnLegacyRow() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        PullRequestModel model = model();
        Issue issue = mockIssue(1001L, "BAR-1");

        PullRequestEntity legacyEntity = mock(PullRequestEntity.class);
        when(legacyEntity.getIssueId()).thenReturn(null);
        when(legacyEntity.getUrl()).thenReturn(model.getUrl());

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] {}, new PullRequestEntity[] { legacyEntity });

        serviceFor(issue, new HashSet<>(Collections.singletonList("FOO-1")))
                .createPullRequest("BAR-1", model);

        // The row was stored under the pre-rename key; the upsert must move it to
        // the canonical key so later lookups and notifications resolve it directly.
        verify(legacyEntity).setIssueKey("BAR-1");
    }

    @Test
    public void testCreatePullRequest_UpdatesNewestDuplicateAndDeletesOlder() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        PullRequestModel model = model();
        Issue issue = mockIssue(1001L, "TEST-1");

        // Racing upserts left two rows for the same issue + URL; the upsert must
        // write to the newest (the row the display shows) and delete the older one.
        PullRequestEntity older = mock(PullRequestEntity.class);
        when(older.getUrl()).thenReturn(model.getUrl());
        when(older.getUpdated()).thenReturn(new Date(1_000L));

        PullRequestEntity newer = mock(PullRequestEntity.class);
        when(newer.getIssueId()).thenReturn(1001L);
        when(newer.getUrl()).thenReturn(model.getUrl());
        when(newer.getUpdated()).thenReturn(new Date(2_000L));

        // First find: ISSUE_ID pool; the legacy exact and case-insensitive finds are empty.
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { older, newer }, new PullRequestEntity[] {});

        serviceFor(issue, Collections.emptySet()).createPullRequest("TEST-1", model);

        verify(activeObjects, never()).create(PullRequestEntity.class);
        verify(activeObjects).delete(older);
        verify(newer).setName(model.getName());
        verify(newer).save();
    }

    @Test
    public void testCreatePullRequest_UpdatesNewestLegacyDuplicateAndDeletesOlder() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        PullRequestModel model = model();
        Issue issue = mockIssue(1001L, "BAR-1");

        // Two pre-ISSUE_ID legacy rows share the URL and arrive older-first: the
        // upsert must obey the same newest-row-wins rule as the ISSUE_ID bucket.
        PullRequestEntity olderLegacy = mock(PullRequestEntity.class);
        when(olderLegacy.getUrl()).thenReturn(model.getUrl());
        when(olderLegacy.getUpdated()).thenReturn(new Date(1_000L));

        PullRequestEntity newerLegacy = mock(PullRequestEntity.class);
        when(newerLegacy.getIssueId()).thenReturn(null);
        when(newerLegacy.getUrl()).thenReturn(model.getUrl());
        when(newerLegacy.getUpdated()).thenReturn(new Date(2_000L));

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] {},
                        new PullRequestEntity[] { olderLegacy, newerLegacy });

        serviceFor(issue, new HashSet<>(Collections.singletonList("FOO-1")))
                .createPullRequest("BAR-1", model);

        verify(activeObjects, never()).create(PullRequestEntity.class);
        verify(activeObjects).delete(olderLegacy);
        verify(newerLegacy).setIssueId(1001L);
        verify(newerLegacy).setName(model.getName());
    }

    @Test
    public void testCreatePullRequest_MatchesRowsStoredWithPaddedUrls() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        PullRequestModel model = model();
        Issue issue = mockIssue(1001L, "TEST-1");

        // Rows written before trim-on-store may hold padded URLs; the upsert must
        // still update them rather than create a duplicate.
        PullRequestEntity padded = mock(PullRequestEntity.class);
        when(padded.getIssueId()).thenReturn(1001L);
        when(padded.getUrl()).thenReturn("  " + model.getUrl() + "  ");
        when(padded.getUpdated()).thenReturn(new Date(1_000L));

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { padded }, new PullRequestEntity[] {});

        serviceFor(issue, Collections.emptySet()).createPullRequest("TEST-1", model);

        verify(activeObjects, never()).create(PullRequestEntity.class);
        verify(padded).setName(model.getName());
        verify(padded).save();
    }

    @Test(expected = NullPointerException.class)
    public void testCreatePullRequest_IssueOverloadRejectsNullIssue() {
        serviceFor(null, Collections.emptySet()).createPullRequest((Issue) null, model());
    }

    @Test
    public void testCreatePullRequest_NullUrlNeverMatchesOrDeletesUrlLessRows() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("false");

        PullRequestModel model = model();
        model.setUrl(null);
        Issue issue = mockIssue(1001L, "TEST-1");

        // The display treats every URL-less row as unique, so a URL-less payload
        // must always create a fresh row — never "match" (and delete) existing ones.
        PullRequestEntity existingUrlLess = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { existingUrlLess });
        PullRequestEntity created = mock(PullRequestEntity.class);
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(created);

        serviceFor(issue, Collections.emptySet()).createPullRequest("TEST-1", model);

        verify(activeObjects).create(PullRequestEntity.class);
        verify(activeObjects, never()).delete(any(PullRequestEntity.class));
        verify(created).save();
    }

    @Test
    public void testGetPullRequests_KeepsOnlyNewestRowPerUrl() {
        Long issueId = 1001L;
        Issue issue = mockIssue(issueId, "TEST-1");

        PullRequestEntity older = mock(PullRequestEntity.class);
        when(older.getID()).thenReturn(1);
        when(older.getIssueId()).thenReturn(issueId);
        when(older.getName()).thenReturn("Older duplicate");
        when(older.getUrl()).thenReturn("http://example.com/pr/1");
        when(older.getUpdated()).thenReturn(new Date(1_000L));

        PullRequestEntity newer = mock(PullRequestEntity.class);
        when(newer.getID()).thenReturn(2);
        when(newer.getIssueId()).thenReturn(issueId);
        when(newer.getName()).thenReturn("Newer duplicate");
        when(newer.getUrl()).thenReturn("http://example.com/pr/1");
        when(newer.getUpdated()).thenReturn(new Date(2_000L));

        // Racing upserts can leave duplicate rows for the same issue + URL; display
        // must keep only the newest.
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { older, newer }, new PullRequestEntity[] {},
                        new PullRequestEntity[] {});

        List<PullRequestModel> models = serviceFor(issue, Collections.emptySet()).getPullRequests("TEST-1");

        assertEquals(1, models.size());
        assertEquals("Newer duplicate", models.get(0).getName());
    }

    @Test
    public void testHasPullRequests_SkipsUnindexableCaseInsensitiveRescue() {
        Issue issue = mockIssue(1001L, "BAR-1");
        when(activeObjects.count(eq(PullRequestEntity.class), any(Query.class))).thenReturn(0, 0);

        boolean hasPullRequests = serviceFor(issue, new HashSet<>(Collections.singletonList("FOO-1")))
                .hasPullRequests("BAR-1");

        assertFalse(hasPullRequests);
        // This runs on every issue view via panel conditions: exactly two indexed
        // counts (ISSUE_ID, then exact legacy keys) and never the UPPER() full scan.
        verify(activeObjects, times(2)).count(eq(PullRequestEntity.class), any(Query.class));
    }

    @Test
    public void testLegacyLookupsOnlyMatchRowsWithoutIssueId() {
        Long issueId = 1001L;
        Issue issue = mockIssue(issueId, "BAR-1");

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] {}, new PullRequestEntity[] {}, new PullRequestEntity[] {});

        serviceFor(issue, new HashSet<>(Collections.singletonList("FOO-1"))).getPullRequests("BAR-1");

        // Pin the WHERE clauses: rows whose non-null ISSUE_ID belongs to another
        // (since-deleted) issue must never be surfaced by the legacy-key fallback.
        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        verify(activeObjects, times(3)).find(eq(PullRequestEntity.class), queries.capture());
        assertEquals("ISSUE_ID = ?", queries.getAllValues().get(0).getWhereClause());
        assertEquals("ISSUE_ID IS NULL AND ISSUE_KEY IN (?, ?)",
                queries.getAllValues().get(1).getWhereClause());
        assertEquals("ISSUE_ID IS NULL AND UPPER(ISSUE_KEY) IN (?, ?)",
                queries.getAllValues().get(2).getWhereClause());
    }

    @Test
    public void testDeletePullRequest_RemovesAllRowsMatchingUrl() {
        Long issueId = 1001L;
        Issue issue = mockIssue(issueId, "TEST-1");

        // Two duplicates for the target URL (one stored padded, pre-trim-on-store)
        // plus an unrelated row: the delete must remove BOTH matches — not just the
        // newest — and leave the unrelated row alone.
        PullRequestEntity older = mock(PullRequestEntity.class);
        when(older.getID()).thenReturn(1);
        when(older.getIssueId()).thenReturn(issueId);
        when(older.getUrl()).thenReturn("  http://example.com/pr/1  ");
        when(older.getUpdated()).thenReturn(new Date(1_000L));

        PullRequestEntity newer = mock(PullRequestEntity.class);
        when(newer.getID()).thenReturn(2);
        when(newer.getIssueId()).thenReturn(issueId);
        when(newer.getUrl()).thenReturn("http://example.com/pr/1");
        when(newer.getUpdated()).thenReturn(new Date(2_000L));

        PullRequestEntity unrelated = mock(PullRequestEntity.class);
        when(unrelated.getID()).thenReturn(3);
        when(unrelated.getIssueId()).thenReturn(issueId);
        when(unrelated.getUrl()).thenReturn("http://example.com/pr/other");
        when(unrelated.getUpdated()).thenReturn(new Date(3_000L));

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { older, newer, unrelated }, new PullRequestEntity[] {},
                        new PullRequestEntity[] {});

        boolean deleted = serviceFor(issue, Collections.emptySet())
                .deletePullRequest(issue, " http://example.com/pr/1 ");

        assertTrue(deleted);
        // One varargs call, rows in newest-first order (findPullRequestEntities sorts).
        verify(activeObjects).delete(newer, older);
        verify(activeObjects, never()).delete(unrelated);
    }

    @Test
    public void testDeletePullRequest_ReturnsFalseWhenNothingMatches() {
        Long issueId = 1001L;
        Issue issue = mockIssue(issueId, "TEST-1");

        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(entity.getID()).thenReturn(1);
        when(entity.getIssueId()).thenReturn(issueId);
        when(entity.getUrl()).thenReturn("http://example.com/pr/1");

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] { entity }, new PullRequestEntity[] {},
                        new PullRequestEntity[] {});

        boolean deleted = serviceFor(issue, Collections.emptySet())
                .deletePullRequest(issue, "http://example.com/pr/other");

        assertFalse(deleted);
        verify(activeObjects, never()).delete(any(PullRequestEntity.class));
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
