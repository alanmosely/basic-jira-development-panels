package it.com.alanmosely.jira.plugin.impl;

import java.util.Date;
import java.util.List;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.alanmosely.jira.plugin.impl.PullRequestServiceImpl;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

@RunWith(AtlassianPluginsTestRunner.class)
public class PullRequestServiceImplIntegrationTest {

    private ActiveObjects activeObjects;
    private PluginSettingsFactory pluginSettingsFactory;
    private PluginSettings pluginSettings;
    private PullRequestServiceImpl pullRequestService;

    private static final String PLUGIN_STORAGE_KEY = "com.alanmosely.jira.plugin.pullrequestadmin";

    @Before
    public void setUp() {
        activeObjects = ComponentAccessor.getOSGiComponentInstanceOfType(ActiveObjects.class);
        pluginSettingsFactory = ComponentAccessor.getOSGiComponentInstanceOfType(PluginSettingsFactory.class);
        pluginSettings = pluginSettingsFactory.createGlobalSettings();

        pluginSettings.put(PLUGIN_STORAGE_KEY + ".notificationsEnabled", "true");

        pullRequestService = new PullRequestServiceImpl(activeObjects, pluginSettingsFactory);
    }

    @After
    public void tearDown() {
        pluginSettings.remove(PLUGIN_STORAGE_KEY + ".notificationsEnabled");
    }

    @Test
    public void testCreateAndRetrievePullRequest() {
        String issueKey = "TEST-1";
        PullRequestModel model = new PullRequestModel();
        model.setName("PR Name");
        model.setUrl("http://example.com/pr/1");
        model.setStatus("Open");
        model.setRepoName("Repo Name");
        model.setRepoUrl("http://example.com/repo");
        model.setBranchName("feature-branch");
        model.setUpdated(new Date());

        pullRequestService.createPullRequest(issueKey, model);

        List<PullRequestModel> models = pullRequestService.getPullRequests(issueKey);

        assertNotNull(models);
        assertEquals(1, models.size());
        PullRequestModel retrievedModel = models.get(0);
        assertEquals(model.getName(), retrievedModel.getName());
        assertEquals(model.getUrl(), retrievedModel.getUrl());
        assertEquals(model.getStatus(), retrievedModel.getStatus());
        assertEquals(model.getRepoName(), retrievedModel.getRepoName());
        assertEquals(model.getRepoUrl(), retrievedModel.getRepoUrl());
        assertEquals(model.getBranchName(), retrievedModel.getBranchName());
        assertNotNull(retrievedModel.getUpdated());
    }

    @Test
    public void testHasPullRequests() {
        String issueKey = "TEST-1";

        boolean hasPRs = pullRequestService.hasPullRequests(issueKey);
        assertFalse(hasPRs);

        PullRequestModel model = new PullRequestModel();
        model.setName("PR Name");
        model.setUrl("http://example.com/pr/1");
        model.setStatus("Open");
        model.setRepoName("Repo Name");
        model.setRepoUrl("http://example.com/repo");
        model.setBranchName("feature-branch");
        model.setUpdated(new Date());

        pullRequestService.createPullRequest(issueKey, model);

        hasPRs = pullRequestService.hasPullRequests(issueKey);
        assertTrue(hasPRs);
    }

    @Test
    public void testCreatePullRequest_WhenProcessingDisabled() {
        pluginSettings.put(PLUGIN_STORAGE_KEY + ".notificationsEnabled", "false");

        String issueKey = "TEST-2";
        PullRequestModel model = new PullRequestModel();
        model.setName("PR Name 2");
        model.setUrl("http://example.com/pr/2");
        model.setStatus("Closed");
        model.setRepoName("Repo Name 2");
        model.setRepoUrl("http://example.com/repo2");
        model.setBranchName("bugfix-branch");
        model.setUpdated(new Date());

        pullRequestService.createPullRequest(issueKey, model);

        List<PullRequestModel> models = pullRequestService.getPullRequests(issueKey);

        assertNotNull(models);
        assertEquals(1, models.size());
        PullRequestModel retrievedModel = models.get(0);
        assertEquals(model.getName(), retrievedModel.getName());
        assertEquals(model.getUrl(), retrievedModel.getUrl());
        assertEquals(model.getStatus(), retrievedModel.getStatus());
        assertEquals(model.getRepoName(), retrievedModel.getRepoName());
        assertEquals(model.getRepoUrl(), retrievedModel.getRepoUrl());
        assertEquals(model.getBranchName(), retrievedModel.getBranchName());
        assertNotNull(retrievedModel.getUpdated());
    }
}
