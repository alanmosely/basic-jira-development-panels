package com.alanmosely.jira.plugin.admin;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.security.request.RequestMethod;
import com.atlassian.jira.security.request.SupportedMethods;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.websudo.WebSudoRequired;

@WebSudoRequired
public class PullRequestAdminAction extends JiraWebActionSupport {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestAdminAction.class);

    private static final String PLUGIN_STORAGE_KEY = "com.alanmosely.jira.plugin.pullrequestadmin";
    private final PluginSettingsFactory pluginSettingsFactory;
    private boolean notificationsEnabled;

    @Inject
    public PullRequestAdminAction(PluginSettingsFactory pluginSettingsFactory) {
        logger.debug("PullRequestAdminAction constructor called");
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String doDefault() {
        logger.debug("Entering doDefault()");
        String value = (String) pluginSettingsFactory.createGlobalSettings()
                .get(PLUGIN_STORAGE_KEY + ".notificationsEnabled");
        notificationsEnabled = value == null || Boolean.parseBoolean(value);
        logger.debug("notificationsEnabled set to: {}", notificationsEnabled);
        return INPUT;
    }

    @Override
    @SupportedMethods({ RequestMethod.GET, RequestMethod.POST })
    public String doExecute() throws Exception {
        logger.debug("Entering doExecute()");
        if (RequestMethod.POST.toString().equals(getHttpRequest().getMethod())) {
            logger.debug("Handling POST request");
            pluginSettingsFactory.createGlobalSettings().put(PLUGIN_STORAGE_KEY + ".notificationsEnabled",
                    Boolean.toString(notificationsEnabled));
            logger.debug("notificationsEnabled set to: {}", notificationsEnabled);
            return SUCCESS;
        } else {
            logger.debug("Handling GET request");
            String value = (String) pluginSettingsFactory.createGlobalSettings()
                    .get(PLUGIN_STORAGE_KEY + ".notificationsEnabled");
            notificationsEnabled = value == null || Boolean.parseBoolean(value);
            logger.debug("notificationsEnabled set to: {}", notificationsEnabled);
            return INPUT;
        }

    }

    public boolean areNotificationsEnabled() {
        logger.debug("areNotificationsEnabled() called, returning: {}", notificationsEnabled);
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        logger.debug("setNotificationsEnabled() called with: {}", notificationsEnabled);
        this.notificationsEnabled = notificationsEnabled;
    }
}
