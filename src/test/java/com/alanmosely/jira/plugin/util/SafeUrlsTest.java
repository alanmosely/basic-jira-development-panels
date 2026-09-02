package com.alanmosely.jira.plugin.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * SafeUrls is the single owner of the plugin's URL policy (REST validation, panel
 * rendering, email links) — these tests pin that policy directly.
 */
public class SafeUrlsTest {

    @Test
    public void acceptsPlainHttpAndHttpsUrls() {
        assertTrue(SafeUrls.isHttpUrl("http://example.com/pr/1"));
        assertTrue(SafeUrls.isHttpUrl("https://example.com/pr/1"));
    }

    @Test
    public void schemeCheckIsCaseInsensitive() {
        assertTrue(SafeUrls.isHttpUrl("HTTP://example.com"));
        assertTrue(SafeUrls.isHttpUrl("HttPs://example.com"));
    }

    @Test
    public void toleratesSurroundingWhitespace() {
        assertTrue(SafeUrls.isHttpUrl("  https://example.com  "));
        assertTrue(SafeUrls.isHttpUrl("\thttps://example.com\n"));
    }

    @Test
    public void rejectsNullEmptyAndBlank() {
        assertFalse(SafeUrls.isHttpUrl(null));
        assertFalse(SafeUrls.isHttpUrl(""));
        assertFalse(SafeUrls.isHttpUrl("   "));
    }

    @Test
    public void rejectsUnsafeSchemes() {
        assertFalse(SafeUrls.isHttpUrl("javascript:alert(1)"));
        assertFalse(SafeUrls.isHttpUrl("data:text/html;base64,PHNjcmlwdD4="));
        assertFalse(SafeUrls.isHttpUrl("ftp://example.com"));
        assertFalse(SafeUrls.isHttpUrl("file:///etc/passwd"));
        assertFalse(SafeUrls.isHttpUrl("//example.com"));
    }

    @Test
    public void rejectsSchemesHiddenBehindTricks() {
        assertFalse(SafeUrls.isHttpUrl("java\nscript:alert(1)"));
        assertFalse(SafeUrls.isHttpUrl(" javascript:alert(1)"));
        assertFalse(SafeUrls.isHttpUrl("httpx://example.com"));
    }

    @Test
    public void rejectsEmbeddedWhitespace() {
        assertFalse(SafeUrls.isHttpUrl("http://example.com/a b"));
        assertFalse(SafeUrls.isHttpUrl("http://example.com/a\rb"));
        assertFalse(SafeUrls.isHttpUrl("http://example.com/a\nb"));
        assertFalse(SafeUrls.isHttpUrl("http://example.com/a\tb"));
    }
}
