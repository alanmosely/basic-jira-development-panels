package com.alanmosely.jira.plugin.tabpanel;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.jira.plugin.issuetabpanel.AbstractIssueAction;
import com.atlassian.jira.plugin.issuetabpanel.IssueTabPanelModuleDescriptor;

public class PullRequestIssueAction extends AbstractIssueAction {

    private final List<PullRequestModel> pullRequests;

    public PullRequestIssueAction(IssueTabPanelModuleDescriptor descriptor, List<PullRequestModel> pullRequests) {
        super(descriptor);
        this.pullRequests = pullRequests;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void populateVelocityParams(@SuppressWarnings("rawtypes") Map params) {
        params.put("pullRequests", pullRequests);
    }

    @Override
    public Date getTimePerformed() {
        return new Date();
    }

    @Override
    public boolean isDisplayActionAllTab() {
        return true;
    }
}
