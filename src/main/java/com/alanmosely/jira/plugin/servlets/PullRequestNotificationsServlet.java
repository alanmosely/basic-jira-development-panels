package com.alanmosely.jira.plugin.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.opensymphony.module.propertyset.PropertyException;
import com.opensymphony.module.propertyset.PropertySet;

public class PullRequestNotificationsServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(PullRequestNotificationsServlet.class);

    private final JiraAuthenticationContext authenticationContext = ComponentAccessor.getJiraAuthenticationContext();
    private final UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        log.debug("Entering doPost method");

        ApplicationUser currentUser = authenticationContext.getLoggedInUser();
        log.debug("Current user: {}", currentUser);

        if (currentUser == null) {
            log.warn("No user is logged in. Sending forbidden response.");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "User must be logged in");
            return;
        }

        String codeNotificationsParam = request.getParameter("codeNotifications");
        log.debug("Received codeNotifications parameter: {}", codeNotificationsParam);

        boolean codeNotifications = "true".equals(codeNotificationsParam);
        log.debug("Parsed codeNotifications value: {}", codeNotifications);

        try {
            PropertySet userProperties = userPropertyManager.getPropertySet(currentUser);
            userProperties.setBoolean("com.alanmosely.jira.plugin.codeNotifications", codeNotifications);
            log.info("Set codeNotifications to {} for user {}", codeNotifications, currentUser.getUsername());
        } catch (PropertyException e) {
            log.error("Error setting codeNotifications for user {}", currentUser.getUsername(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to update preferences");
            return;
        }

        String redirectUrl = request.getContextPath() + "/secure/ViewProfile.jspa";
        log.debug("Redirecting to {}", redirectUrl);
        response.sendRedirect(redirectUrl);

        log.debug("Exiting doPost method");
    }
}
