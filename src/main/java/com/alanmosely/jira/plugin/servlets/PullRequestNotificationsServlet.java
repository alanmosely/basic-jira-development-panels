package com.alanmosely.jira.plugin.servlets;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.util.SettingsKeys;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.xsrf.XsrfTokenGenerator;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.opensymphony.module.propertyset.PropertyException;
import com.opensymphony.module.propertyset.PropertySet;

public class PullRequestNotificationsServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(PullRequestNotificationsServlet.class);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        log.debug("Entering doPost method");

        JiraAuthenticationContext authenticationContext = ComponentAccessor.getJiraAuthenticationContext();
        ApplicationUser currentUser = authenticationContext.getLoggedInUser();

        if (currentUser == null) {
            log.warn("No user is logged in. Sending forbidden response.");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "User must be logged in");
            return;
        }

        XsrfTokenGenerator tokenGenerator = ComponentAccessor.getComponent(XsrfTokenGenerator.class);
        String token = request.getParameter("atl_token");
        if (tokenGenerator == null || !tokenGenerator.validateToken(request, token)) {
            log.warn("Rejected notification preference change with missing or invalid XSRF token for user {}",
                    currentUser.getUsername());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing or invalid XSRF token");
            return;
        }

        boolean codeNotifications = "true".equals(request.getParameter("codeNotifications"));

        try {
            UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();
            PropertySet userProperties = userPropertyManager.getPropertySet(currentUser);
            userProperties.setBoolean(SettingsKeys.CODE_NOTIFICATIONS_USER_PROPERTY, codeNotifications);
            log.info("Set codeNotifications to {} for user {}", codeNotifications, currentUser.getUsername());
        } catch (PropertyException e) {
            log.error("Error setting codeNotifications for user {}", currentUser.getUsername(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to update preferences");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/secure/ViewProfile.jspa");
    }
}
