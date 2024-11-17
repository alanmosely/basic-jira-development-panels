package it.com.alanmosely.jira.plugin.impl;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.alanmosely.jira.plugin.impl.PullRequestServiceImpl;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;

import net.java.ao.Query;

@RunWith(AtlassianPluginsTestRunner.class)
public class PullRequestServiceImplTest {

    private ActiveObjects activeObjects;
    private PullRequestServiceImpl pullRequestService;

    @Before
    public void setUp() {
        activeObjects = mock(ActiveObjects.class);
        pullRequestService = new PullRequestServiceImpl(activeObjects);
    }

    @Test
    public void testCreatePullRequest() {
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

        pullRequestService.createPullRequest(issueKey, model);

        verify(entity).setIssueKey(issueKey);
        verify(entity).setName(model.getName());
        verify(entity).setUrl(model.getUrl());
        verify(entity).setStatus(model.getStatus());
        verify(entity).setRepoName(model.getRepoName());
        verify(entity).setRepoUrl(model.getRepoUrl());
        verify(entity).setBranchName(model.getBranchName());
        verify(entity).setUpdated(any(Date.class));
        verify(entity).save();
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

}
