package com.alanmosely.jira.plugin.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;

/**
 * Pins WHO gets emailed by sendNotifications — the assignee/reporter/watchers
 * union, the per-user opt-in gate, and failure isolation — via the protected
 * seams, without touching ComponentAccessor.
 */
public class PullRequestNotificationRecipientsTest {

    private static class RecordingService extends PullRequestServiceImpl {
        private final Issue issue;
        private final Collection<ApplicationUser> watchers;
        private final Set<ApplicationUser> optedIn;
        private final Set<ApplicationUser> failing;
        final List<ApplicationUser> emailed = new ArrayList<>();

        RecordingService(Issue issue, Collection<ApplicationUser> watchers, Set<ApplicationUser> optedIn,
                Set<ApplicationUser> failing) {
            super(mock(ActiveObjects.class), mock(PluginSettingsFactory.class), mock(TransactionTemplate.class));
            this.issue = issue;
            this.watchers = watchers;
            this.optedIn = optedIn;
            this.failing = failing;
        }

        @Override
        protected Issue resolveIssue(String issueKey) {
            return issue;
        }

        @Override
        protected Collection<ApplicationUser> watchers(Issue issue) {
            return watchers;
        }

        @Override
        protected boolean isOptedIn(ApplicationUser user) {
            return optedIn.contains(user);
        }

        @Override
        protected boolean sendEmailToUser(ApplicationUser user, Issue issue, PullRequestEntity entity) {
            emailed.add(user);
            return !failing.contains(user);
        }
    }

    private static Issue issue(ApplicationUser assignee, ApplicationUser reporter) {
        Issue issue = mock(Issue.class);
        when(issue.getKey()).thenReturn("TEST-1");
        when(issue.getAssignee()).thenReturn(assignee);
        when(issue.getReporter()).thenReturn(reporter);
        return issue;
    }

    private static PullRequestEntity entity() {
        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(entity.getIssueKey()).thenReturn("TEST-1");
        return entity;
    }

    private static ApplicationUser user(String name) {
        ApplicationUser user = mock(ApplicationUser.class);
        when(user.getUsername()).thenReturn(name);
        return user;
    }

    @Test
    public void optedOutRecipientsGetNoEmail() {
        ApplicationUser assignee = user("assignee");
        ApplicationUser watcher = user("watcher");
        RecordingService service = new RecordingService(
                issue(assignee, null),
                Collections.singletonList(watcher),
                Collections.singleton(watcher),
                Collections.emptySet());

        service.sendNotifications(entity());

        assertEquals(Collections.singletonList(watcher), service.emailed);
    }

    @Test
    public void assigneeAlsoReporterAndWatcherIsEmailedOnce() {
        ApplicationUser user = user("everything");
        RecordingService service = new RecordingService(
                issue(user, user),
                Collections.singletonList(user),
                Collections.singleton(user),
                Collections.emptySet());

        service.sendNotifications(entity());

        assertEquals(1, service.emailed.size());
    }

    @Test
    public void missingAssigneeAndReporterAreTolerated() {
        ApplicationUser watcher = user("watcher");
        RecordingService service = new RecordingService(
                issue(null, null),
                Collections.singletonList(watcher),
                Collections.singleton(watcher),
                Collections.emptySet());

        service.sendNotifications(entity());

        assertEquals(Collections.singletonList(watcher), service.emailed);
    }

    @Test
    public void unresolvableIssueSendsNothing() {
        ApplicationUser watcher = user("watcher");
        RecordingService service = new RecordingService(
                null,
                Collections.singletonList(watcher),
                Collections.singleton(watcher),
                Collections.emptySet());

        service.sendNotifications(entity());

        assertTrue(service.emailed.isEmpty());
    }

    @Test
    public void failedQueueDoesNotAbortRemainingRecipients() {
        ApplicationUser first = user("first");
        ApplicationUser second = user("second");
        RecordingService service = new RecordingService(
                issue(null, null),
                Arrays.asList(first, second),
                new HashSet<>(Arrays.asList(first, second)),
                Collections.singleton(first));

        service.sendNotifications(entity());

        assertEquals(2, service.emailed.size());
        assertTrue(service.emailed.containsAll(Arrays.asList(first, second)));
    }
}
