package com.alanmosely.jira.plugin.context;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.plugin.web.ContextProvider;
import com.opensymphony.module.propertyset.PropertyException;
import com.opensymphony.module.propertyset.PropertySet;

public class PullRequestContextProvider implements ContextProvider {

    private static final Logger log = LoggerFactory.getLogger(PullRequestContextProvider.class);

    @Override
    public void init(Map<String, String> params) {
    }

    @Override
    public Map<String, Object> getContextMap(Map<String, Object> context) {
        log.debug("Entering getContextMap with context: {}", context);

        Map<String, Object> newContext = new HashMap<>();

        try {
            ApplicationUser currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
            log.debug("Current user: {}", currentUser);

            if (currentUser != null) {
                UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();
                PropertySet userProperties = userPropertyManager.getPropertySet(currentUser);

                boolean codeNotifications = userProperties.getBoolean("com.alanmosely.jira.plugin.codeNotifications");
                log.debug("codeNotifications for user {}: {}", currentUser.getUsername(), codeNotifications);

                newContext.put("codeNotifications", codeNotifications);
            } else {
                log.warn("No user is currently logged in.");
                newContext.put("codeNotifications", false);
            }
        } catch (PropertyException e) {
            log.error("An error occurred while getting the context map.", e);
            newContext.put("codeNotifications", false);
        }

        log.debug("Exiting getContextMap with newContext: {}", newContext);
        return newContext;
    }
}
