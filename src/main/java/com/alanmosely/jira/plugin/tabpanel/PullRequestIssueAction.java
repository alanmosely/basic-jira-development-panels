package com.alanmosely.jira.plugin.tabpanel;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.jira.plugin.issuetabpanel.AbstractIssueAction;
import com.atlassian.jira.plugin.issuetabpanel.IssueTabPanelModuleDescriptor;

public class PullRequestIssueAction extends AbstractIssueAction {

    private final List<PullRequestModel> pullRequests;
    private final Date timePerformed;

    public PullRequestIssueAction(IssueTabPanelModuleDescriptor descriptor, List<PullRequestModel> pullRequests) {
        super(descriptor);
        this.pullRequests = pullRequests;
        this.timePerformed = newestUpdate(pullRequests);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void populateVelocityParams(@SuppressWarnings("rawtypes") Map params) {
        params.put("pullRequests", pullRequests);
    }

    /**
     * The Activity "All" tab orders entries by this timestamp; report the newest
     * pull request update rather than the render time so the entry doesn't always
     * sort as "just now".
     */
    @Override
    public Date getTimePerformed() {
        return timePerformed;
    }

    private static Date newestUpdate(List<PullRequestModel> pullRequests) {
        Date newest = null;
        if (pullRequests != null) {
            for (PullRequestModel pullRequest : pullRequests) {
                Date updated = pullRequest.getUpdated();
                if (updated != null && (newest == null || updated.after(newest))) {
                    newest = updated;
                }
            }
        }
        return newest != null ? newest : new Date();
    }

    @Override
    public boolean isDisplayActionAllTab() {
        return true;
    }
}
