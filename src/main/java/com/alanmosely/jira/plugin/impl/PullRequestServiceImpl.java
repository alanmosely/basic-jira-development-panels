package com.alanmosely.jira.plugin.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.watchers.WatcherManager;
import com.atlassian.jira.mail.Email;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.mail.queue.SingleMailQueueItem;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.opensymphony.module.propertyset.PropertySet;

import net.java.ao.Query;

@Named
public class PullRequestServiceImpl implements PullRequestService {

    private final ActiveObjects activeObjects;

    @Inject
    public PullRequestServiceImpl(@ComponentImport ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public void createPullRequest(String issueKey, PullRequestModel model) {
        PullRequestEntity entity = findOrCreateEntity(issueKey, model.getUrl());
        updateEntityFromModel(entity, model);
        entity.save();
        processPullRequest(entity);
    }

    private void processPullRequest(PullRequestEntity entity) {
        Set<ApplicationUser> involvedUsers = new HashSet<>();
        Issue issue = ComponentAccessor.getIssueManager().getIssueObject(entity.getIssueKey());
        if (issue.getAssignee() != null) {
            involvedUsers.add(issue.getAssignee());
        }
        if (issue.getReporter() != null) {
            involvedUsers.add(issue.getReporter());
        }
        WatcherManager watcherManager = ComponentAccessor.getWatcherManager();
        involvedUsers.addAll(watcherManager.getWatchersUnsorted(issue));

        UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();

        for (ApplicationUser user : involvedUsers) {
            if (user == null)
                continue;
            PropertySet userProperties = userPropertyManager.getPropertySet(user);
            boolean codeNotifications = userProperties.getBoolean("com.alanmosely.jira.plugin.codeNotifications");
            if (codeNotifications) {
                sendEmailToUser(user, issue, entity);
            }
        }
    }

    private void sendEmailToUser(ApplicationUser user, Issue issue, PullRequestEntity entity) {
        String issueUrl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl") + "/browse/"
                + issue.getKey();

        Email email = new Email(user.getEmailAddress());
        email.setSubject("Code update on " + issue.getKey());
        String emailBody = "<html>"
                + "<body>"
                + "<p>Hello " + user.getDisplayName() + ",</p>"
                + "<p>There has been a code update related to <strong><a href='" + issueUrl + "'>" + issue.getKey()
                + "</a></strong>.</p>"
                + "<br/><p><strong>Details:</strong></p>"
                + "<ul>"
                + "<li><strong>PR:</strong> <a href='" + entity.getUrl() + "'>" + entity.getName() + "</a></li>"
                + "<li><strong>Repository:</strong> <a href='" + entity.getRepoUrl() + "'>" + entity.getRepoName()
                + "</a></li>"
                + "<li><strong>Branch:</strong> " + entity.getBranchName() + "</li>"
                + "<li><strong>Status:</strong> " + entity.getStatus() + "</li>"
                + "</ul>"
                + "</body>"
                + "</html>";
        email.setBody(emailBody);
        email.setMimeType("text/html");

        SingleMailQueueItem mailItem = new SingleMailQueueItem(email);
        ComponentAccessor.getMailQueue().addItem(mailItem);
    }

    private PullRequestEntity findOrCreateEntity(String issueKey, String url) {
        PullRequestEntity[] entities = activeObjects.find(PullRequestEntity.class,
                Query.select().where("ISSUE_KEY = ? AND URL = ?", issueKey, url));
        if (entities.length > 0) {
            return entities[0];
        } else {
            PullRequestEntity entity = activeObjects.create(PullRequestEntity.class);
            entity.setIssueKey(issueKey);
            return entity;
        }
    }

    private void updateEntityFromModel(PullRequestEntity entity, PullRequestModel model) {
        entity.setName(model.getName());
        entity.setUrl(model.getUrl());
        entity.setStatus(model.getStatus());
        entity.setRepoName(model.getRepoName());
        entity.setRepoUrl(model.getRepoUrl());
        entity.setBranchName(model.getBranchName());
        entity.setUpdated(new Date());
    }

    @Override
    public List<PullRequestModel> getPullRequests(String issueKey) {
        PullRequestEntity[] entities = activeObjects.find(PullRequestEntity.class,
                Query.select().where("ISSUE_KEY = ?", issueKey));
        return mapEntitiesToModels(entities);
    }

    private List<PullRequestModel> mapEntitiesToModels(PullRequestEntity[] entities) {
        List<PullRequestModel> models = new ArrayList<>();
        for (PullRequestEntity entity : entities) {
            models.add(mapEntityToModel(entity));
        }
        return models;
    }

    private PullRequestModel mapEntityToModel(PullRequestEntity entity) {
        PullRequestModel model = new PullRequestModel();
        model.setName(entity.getName());
        model.setUrl(entity.getUrl());
        model.setStatus(entity.getStatus());
        model.setRepoName(entity.getRepoName());
        model.setRepoUrl(entity.getRepoUrl());
        model.setBranchName(entity.getBranchName());
        model.setUpdated(entity.getUpdated());
        return model;
    }

    @Override
    public boolean hasPullRequests(String issueKey) {
        int count = activeObjects.count(PullRequestEntity.class, Query.select()
                .where("ISSUE_KEY = ?", issueKey)
                .limit(1));
        return count > 0;
    }
}
