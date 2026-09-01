package com.alanmosely.jira.plugin.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Single owner of the plugin's URL policy: only well-formed http(s) URLs may be
 * stored, rendered as links, or embedded in notification emails.
 */
public final class SafeUrls {

    private SafeUrls() {
    }

    public static boolean isHttpUrl(String url) {
        String trimmed = StringUtils.trimToEmpty(url);
        if (trimmed.isEmpty() || StringUtils.containsAny(trimmed, ' ', '\r', '\n', '\t')) {
            return false;
        }
        return StringUtils.startsWithIgnoreCase(trimmed, "http://")
                || StringUtils.startsWithIgnoreCase(trimmed, "https://");
    }
}
