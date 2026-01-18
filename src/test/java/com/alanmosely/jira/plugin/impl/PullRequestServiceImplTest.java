package com.alanmosely.jira.plugin.impl;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import net.java.ao.Query;

public class PullRequestServiceImplTest {

    private ActiveObjects activeObjects;
    private PluginSettingsFactory pluginSettingsFactory;
    private PluginSettings pluginSettings;
    private PullRequestServiceImpl pullRequestService;

    private static final String PLUGIN_STORAGE_KEY = "com.alanmosely.jira.plugin.pullrequestadmin";

    private static class TestPullRequestServiceImpl extends PullRequestServiceImpl {
        private final Long issueId;

        TestPullRequestServiceImpl(ActiveObjects activeObjects, PluginSettingsFactory pluginSettingsFactory,
                Long issueId) {
            super(activeObjects, pluginSettingsFactory);
            this.issueId = issueId;
        }

        @Override
        protected Long resolveIssueId(String issueKey) {
            return issueId;
        }
    }

    @Before
    public void setUp() {
        activeObjects = mock(ActiveObjects.class);
        pluginSettingsFactory = mock(PluginSettingsFactory.class);
        pluginSettings = mock(PluginSettings.class);

        when(pluginSettingsFactory.createGlobalSettings()).thenReturn(pluginSettings);

        pullRequestService = new PullRequestServiceImpl(activeObjects, pluginSettingsFactory);
    }

    @Test
    public void testCreatePullRequest_WhenProcessingEnabled() {
        when(pluginSettings.get(PLUGIN_STORAGE_KEY + ".notificationsEnabled")).thenReturn("true");

        String issueKey = "TEST-1";
        PullRequestModel model = new PullRequestModel();
        model.setName("PR Name");
        model.setUrl("http://example.com/pr/1");
        model.setStatus("Open");
        model.setRepoName("Repo Name");
        model.setRepoUrl("http://example.com/repo");
        model.setBranchName("feature-branch");
        model.setUpdated(new Date());

        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(entity);

        PullRequestServiceImpl spyService = spy(pullRequestService);
        doNothing().when(spyService).sendNotifications(any(PullRequestEntity.class));

        spyService.createPullRequest(issueKey, model);

        verify(entity).setIssueKey(issueKey);
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

        String issueKey = "TEST-1";
        PullRequestModel model = new PullRequestModel();
        model.setName("PR Name");
        model.setUrl("http://example.com/pr/1");
        model.setStatus("Open");
        model.setRepoName("Repo Name");
        model.setRepoUrl("http://example.com/repo");
        model.setBranchName("feature-branch");
        model.setUpdated(new Date());

        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class))).thenReturn(new PullRequestEntity[] {});
        when(activeObjects.create(PullRequestEntity.class)).thenReturn(entity);

        PullRequestServiceImpl spyService = spy(pullRequestService);
        doNothing().when(spyService).sendNotifications(any(PullRequestEntity.class));

        spyService.createPullRequest(issueKey, model);

        verify(entity).setIssueKey(issueKey);
        verify(entity).setName(model.getName());
        verify(entity).setUrl(model.getUrl());
        verify(entity).setStatus(model.getStatus());
        verify(entity).setRepoName(model.getRepoName());
        verify(entity).setRepoUrl(model.getRepoUrl());
        verify(entity).setBranchName(model.getBranchName());
        verify(entity).setUpdated(any(Date.class));
        verify(entity).save();

        verify(spyService, never()).sendNotifications(any(PullRequestEntity.class));
    }

    @Test
    public void testGetPullRequests() {
        String issueKey = "TEST-1";
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

        List<PullRequestModel> models = pullRequestService.getPullRequests(issueKey);

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
    public void testHasPullRequests() {
        String issueKey = "TEST-1";
        when(activeObjects.count(eq(PullRequestEntity.class), any(Query.class))).thenReturn(1);

        boolean result = pullRequestService.hasPullRequests(issueKey);

        assertTrue(result);
    }

    @Test
    public void testGetPullRequestsBackfillsIssueIdForRenamedProjectKey() {
        String issueKey = "BAR-1";
        String legacyIssueKey = "FOO-1";
        Long issueId = 1001L;

        PullRequestEntity legacyEntity = mock(PullRequestEntity.class);
        when(legacyEntity.getIssueId()).thenReturn(null);
        when(legacyEntity.getIssueKey()).thenReturn(legacyIssueKey);
        when(legacyEntity.getName()).thenReturn("PR Name");
        when(legacyEntity.getUrl()).thenReturn("http://example.com/pr/1");
        when(legacyEntity.getStatus()).thenReturn("Open");
        when(legacyEntity.getRepoName()).thenReturn("Repo Name");
        when(legacyEntity.getRepoUrl()).thenReturn("http://example.com/repo");
        when(legacyEntity.getBranchName()).thenReturn("feature-branch");
        when(legacyEntity.getUpdated()).thenReturn(new Date());

        when(activeObjects.find(eq(PullRequestEntity.class), any(Query.class)))
                .thenReturn(new PullRequestEntity[] {}, new PullRequestEntity[] { legacyEntity });

        PullRequestServiceImpl service = new TestPullRequestServiceImpl(activeObjects, pluginSettingsFactory, issueId);
        List<PullRequestModel> models = service.getPullRequests(issueKey);

        assertNotNull(models);
        assertEquals(1, models.size());
        verify(legacyEntity).setIssueId(issueId);
        verify(legacyEntity).save();
    }
}
