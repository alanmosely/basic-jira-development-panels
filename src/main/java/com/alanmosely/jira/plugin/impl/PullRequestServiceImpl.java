package com.alanmosely.jira.plugin.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.watchers.WatcherManager;
import com.atlassian.jira.mail.Email;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.mail.queue.SingleMailQueueItem;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.opensymphony.module.propertyset.PropertyException;
import com.opensymphony.module.propertyset.PropertySet;

import net.java.ao.Query;

@Named
public class PullRequestServiceImpl implements PullRequestService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestServiceImpl.class);
    private static final String PLUGIN_STORAGE_KEY = "com.alanmosely.jira.plugin.pullrequestadmin";

    private final ActiveObjects activeObjects;
    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public PullRequestServiceImpl(@ComponentImport ActiveObjects activeObjects,
            @ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.activeObjects = activeObjects;
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public void createPullRequest(String issueKey, PullRequestModel model) {
        log.debug("Creating pull request for issueKey: {}, model: {}", issueKey, model);

        try {
            PullRequestEntity entity = findOrCreateEntity(issueKey, model.getUrl());
            updateEntityFromModel(entity, model);
            entity.save();
            log.info("Pull request entity saved for issueKey: {}", issueKey);

            if (areNotificationsEnabled()) {
                sendNotifications(entity);
            } else {
                log.info("Pull request processing is disabled by administrator.");
            }
        } catch (Exception e) {
            log.error("Error creating pull request for issueKey: {}", issueKey, e);
        }
    }

    private boolean areNotificationsEnabled() {
        String value = (String) pluginSettingsFactory.createGlobalSettings()
                .get(PLUGIN_STORAGE_KEY + ".notificationsEnabled");
        return value == null || Boolean.parseBoolean(value);
    }

    void sendNotifications(PullRequestEntity entity) {
        log.debug("Processing pull request entity for issueKey: {}", entity.getIssueKey());

        try {
            Set<ApplicationUser> involvedUsers = new HashSet<>();
            Issue issue = ComponentAccessor.getIssueManager().getIssueObject(entity.getIssueKey());

            if (issue == null) {
                log.warn("Issue not found for issueKey: {}", entity.getIssueKey());
                return;
            }

            if (issue.getAssignee() != null) {
                involvedUsers.add(issue.getAssignee());
                log.debug("Added assignee to involved users: {}", issue.getAssignee().getUsername());
            }

            if (issue.getReporter() != null) {
                involvedUsers.add(issue.getReporter());
                log.debug("Added reporter to involved users: {}", issue.getReporter().getUsername());
            }

            WatcherManager watcherManager = ComponentAccessor.getWatcherManager();
            Collection<ApplicationUser> watchers = watcherManager.getWatchersUnsorted(issue);
            involvedUsers.addAll(watchers);
            log.debug("Added {} watchers to involved users", watchers.size());

            UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();

            for (ApplicationUser user : involvedUsers) {
                if (user == null) {
                    log.warn("Encountered null user in involved users set");
                    continue;
                }

                PropertySet userProperties = userPropertyManager.getPropertySet(user);
                boolean codeNotifications = userProperties.getBoolean("com.alanmosely.jira.plugin.codeNotifications");
                log.debug("User {} codeNotifications: {}", user.getUsername(), codeNotifications);

                if (codeNotifications) {
                    sendEmailToUser(user, issue, entity);
                    log.info("Sent email to user: {}", user.getUsername());
                } else {
                    log.debug("User {} has codeNotifications disabled", user.getUsername());
                }
            }
        } catch (DataAccessException | PropertyException e) {
            log.error("Error processing pull request for issueKey: {}", entity.getIssueKey(), e);
        }
    }

    private void sendEmailToUser(ApplicationUser user, Issue issue, PullRequestEntity entity) {
        log.debug("Sending email to user: {} for issueKey: {}", user.getUsername(), issue.getKey());

        try {
            String issueUrl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl")
                    + "/browse/" + issue.getKey();

            String issueTitle = issue.getSummary();

            String emailSubject = "("
                    + issue.getKey() + ": "
                    + issueTitle + ") ["
                    + entity.getStatus() + "] "
                    + entity.getRepoName() + " - "
                    + entity.getName();

            Email email = new Email(user.getEmailAddress());
            email.setSubject(emailSubject);

            String emailBody = "<html>"
                    + "<body>"
                    + "<p>There has been a code update related to "
                    + "<strong><a href='" + issueUrl + "'>" + issue.getKey() + "</a></strong> (" + issueTitle + ").</p>"
                    + "<br/><p><strong>Details:</strong></p>"
                    + "<ul>"
                    + "<li><strong>Pull Request:</strong> <a href='" + entity.getUrl() + "'>" + entity.getName()
                    + "</a></li>"
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
            log.debug("Email queued for user: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Error sending email to user: {}", user.getUsername(), e);
        }
    }

    private PullRequestEntity findOrCreateEntity(String issueKey, String url) {
        log.debug("Finding or creating PullRequestEntity for issueKey: {}, url: {}", issueKey, url);

        PullRequestEntity[] entities = activeObjects.find(PullRequestEntity.class,
                Query.select().where("ISSUE_KEY = ? AND URL = ?", issueKey, url));

        if (entities.length > 0) {
            log.debug("Found existing PullRequestEntity for issueKey: {}, url: {}", issueKey, url);
            return entities[0];
        } else {
            PullRequestEntity entity = activeObjects.create(PullRequestEntity.class);
            entity.setIssueKey(issueKey);
            log.debug("Created new PullRequestEntity for issueKey: {}", issueKey);
            return entity;
        }
    }

    private void updateEntityFromModel(PullRequestEntity entity, PullRequestModel model) {
        log.debug("Updating PullRequestEntity from model for issueKey: {}", entity.getIssueKey());

        entity.setName(model.getName());
        entity.setUrl(model.getUrl());
        entity.setStatus(model.getStatus());
        entity.setRepoName(model.getRepoName());
        entity.setRepoUrl(model.getRepoUrl());
        entity.setBranchName(model.getBranchName());
        entity.setUpdated(new Date());

        log.debug("Updated PullRequestEntity: {}", entity);
    }

    @Override
    public List<PullRequestModel> getPullRequests(String issueKey) {
        log.debug("Getting pull requests for issueKey: {}", issueKey);

        try {
            PullRequestEntity[] entities = activeObjects.find(
                    PullRequestEntity.class,
                    Query.select()
                            .where("ISSUE_KEY = ?", issueKey).alias(PullRequestEntity.class, "pullrequest")
                            .order("pullrequest.UPDATED DESC"));

            List<PullRequestModel> models = mapEntitiesToModels(entities);
            log.debug("Retrieved {} pull requests for issueKey: {}", models.size(), issueKey);
            return models;
        } catch (Exception e) {
            log.error("Error getting pull requests for issueKey: {}", issueKey, e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean hasPullRequests(String issueKey) {
        log.debug("Checking if issueKey {} has pull requests", issueKey);

        try {
            int count = activeObjects.count(PullRequestEntity.class, Query.select()
                    .where("ISSUE_KEY = ?", issueKey)
                    .limit(1));

            boolean hasPullRequests = count > 0;
            log.debug("IssueKey {} has pull requests: {}", issueKey, hasPullRequests);
            return hasPullRequests;
        } catch (Exception e) {
            log.error("Error checking pull requests for issueKey: {}", issueKey, e);
            return false;
        }
    }

    private List<PullRequestModel> mapEntitiesToModels(PullRequestEntity[] entities) {
        log.debug("Mapping {} PullRequestEntities to PullRequestModels", entities.length);

        List<PullRequestModel> models = new ArrayList<>();
        for (PullRequestEntity entity : entities) {
            models.add(mapEntityToModel(entity));
        }

        log.debug("Mapped entities to models");
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

        log.debug("Mapped PullRequestEntity to PullRequestModel: {}", model);
        return model;
    }
}
