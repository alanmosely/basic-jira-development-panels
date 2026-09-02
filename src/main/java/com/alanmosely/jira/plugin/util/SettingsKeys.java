package com.alanmosely.jira.plugin.util;

/**
 * Persisted key names shared across the plugin: SAL plugin-settings keys used by
 * the admin screen, the REST resource and the service, plus the per-user Jira
 * property behind the profile toggle. These are persisted names — changing them
 * orphans existing settings.
 */
public final class SettingsKeys {

    public static final String PLUGIN_STORAGE_KEY = "com.alanmosely.jira.plugin.pullrequestadmin";
    public static final String NOTIFICATIONS_ENABLED_KEY = PLUGIN_STORAGE_KEY + ".notificationsEnabled";
    public static final String API_USER_KEY = PLUGIN_STORAGE_KEY + ".apiUser";

    /** Jira user property holding the per-user notification opt-in. */
    public static final String CODE_NOTIFICATIONS_USER_PROPERTY = "com.alanmosely.jira.plugin.codeNotifications";

    private SettingsKeys() {
    }
}
