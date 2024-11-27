package com.alanmosely.jira.plugin.tabpanel;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.jira.plugin.issuetabpanel.AbstractIssueAction;
import com.atlassian.jira.plugin.issuetabpanel.IssueTabPanelModuleDescriptor;

public class PullRequestIssueAction extends AbstractIssueAction {

    private static final Logger log = LoggerFactory.getLogger(PullRequestIssueAction.class);

    private final List<PullRequestModel> pullRequests;

    public PullRequestIssueAction(IssueTabPanelModuleDescriptor descriptor, List<PullRequestModel> pullRequests) {
        super(descriptor);
        this.pullRequests = pullRequests;
        log.debug("PullRequestIssueAction initialized with {} pull requests",
                pullRequests != null ? pullRequests.size() : 0);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void populateVelocityParams(@SuppressWarnings("rawtypes") Map params) {
        log.debug("Populating Velocity parameters with pull requests");
        try {
            params.put("pullRequests", pullRequests);
            log.debug("Added pullRequests to Velocity params");
        } catch (Exception e) {
            log.error("Error populating Velocity parameters", e);
        }
    }

    @Override
    public Date getTimePerformed() {
        Date timePerformed = new Date();
        log.debug("getTimePerformed called, returning {}", timePerformed);
        return timePerformed;
    }

    @Override
    public boolean isDisplayActionAllTab() {
        log.debug("isDisplayActionAllTab called, returning true");
        return true;
    }
}
