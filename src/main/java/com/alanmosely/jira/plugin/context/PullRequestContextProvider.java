package com.alanmosely.jira.plugin.context;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.util.SettingsKeys;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.security.xsrf.XsrfTokenGenerator;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.jira.web.ExecutingHttpRequest;
import com.atlassian.plugin.web.ContextProvider;
import com.opensymphony.module.propertyset.PropertyException;
import com.opensymphony.module.propertyset.PropertySet;

public class PullRequestContextProvider implements ContextProvider {

    private static final Logger log = LoggerFactory.getLogger(PullRequestContextProvider.class);

    private static final String NOTIFICATIONS_SERVLET_PATH = "/plugins/servlet/pullrequest-notifications";

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

                boolean codeNotifications = userProperties
                        .getBoolean(SettingsKeys.CODE_NOTIFICATIONS_USER_PROPERTY);
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

        newContext.put("pluginUrl", notificationsServletUrl());
        newContext.put("atlToken", currentXsrfToken());

        log.debug("Exiting getContextMap with newContext: {}", newContext);
        return newContext;
    }

    /**
     * Prefer a context-relative URL from the live request so the form posts back
     * to whatever host the user is actually on; jira.baseurl is only a fallback
     * for render paths with no request (it can point at a different proxy host,
     * which would break the same-origin XSRF check).
     */
    private String notificationsServletUrl() {
        HttpServletRequest request = ExecutingHttpRequest.get();
        String prefix = request != null ? request.getContextPath()
                : ComponentAccessor.getApplicationProperties().getString(APKeys.JIRA_BASEURL);
        return StringUtils.defaultString(prefix) + NOTIFICATIONS_SERVLET_PATH;
    }

    private String currentXsrfToken() {
        try {
            XsrfTokenGenerator tokenGenerator = ComponentAccessor.getComponent(XsrfTokenGenerator.class);
            if (tokenGenerator == null || ExecutingHttpRequest.get() == null) {
                // An empty token renders a form whose submit will be rejected by the
                // servlet's XSRF check — make the cause findable at render time.
                log.warn("No XSRF token available while rendering the notifications panel; "
                        + "the notification toggle will be rejected until the page is reloaded in a request context");
                return "";
            }
            return tokenGenerator.generateToken(ExecutingHttpRequest.get());
        } catch (Exception e) {
            log.warn("Unable to generate XSRF token for notifications panel", e);
            return "";
        }
    }
}
