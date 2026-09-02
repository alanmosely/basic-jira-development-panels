package com.alanmosely.jira.plugin.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.atlassian.jira.issue.Issue;

/**
 * Notification emails are hand-assembled HTML fed from REST-supplied (attacker-
 * influenced) fields; these tests pin the escaping and safe-link discipline that
 * AGENTS.md mandates for every interpolated value.
 */
public class PullRequestEmailContentTest {

    private static Issue issue(String key, String summary) {
        Issue issue = mock(Issue.class);
        when(issue.getKey()).thenReturn(key);
        when(issue.getSummary()).thenReturn(summary);
        return issue;
    }

    private static PullRequestEntity entity(String name, String url, String status, String repoName,
            String repoUrl, String branchName) {
        PullRequestEntity entity = mock(PullRequestEntity.class);
        when(entity.getName()).thenReturn(name);
        when(entity.getUrl()).thenReturn(url);
        when(entity.getStatus()).thenReturn(status);
        when(entity.getRepoName()).thenReturn(repoName);
        when(entity.getRepoUrl()).thenReturn(repoUrl);
        when(entity.getBranchName()).thenReturn(branchName);
        return entity;
    }

    @Test
    public void bodyEscapesHtmlInEveryInterpolatedField() {
        Issue issue = issue("TEST-1", "Summary with <b>markup</b> & ampersand");
        PullRequestEntity entity = entity(
                "<script>alert('x')</script>",
                "https://example.com/pr/1",
                "Open<img src=x onerror=alert(1)>",
                "Repo \"quoted\"",
                "https://example.com/repo",
                "branch</li>");

        String body = PullRequestServiceImpl.buildEmailBody("https://jira.example.com/browse/TEST-1", issue, entity);

        assertFalse(body.contains("<script>"));
        assertTrue(body.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
        assertFalse(body.contains("<img"));
        assertTrue(body.contains("Summary with &lt;b&gt;markup&lt;/b&gt; &amp; ampersand"));
        assertTrue(body.contains("Repo &quot;quoted&quot;"));
        assertTrue(body.contains("branch&lt;/li&gt;"));
    }

    @Test
    public void bodyNeverLinksUnsafeUrlSchemes() {
        Issue issue = issue("TEST-1", "Summary");
        PullRequestEntity entity = entity("PR", "javascript:alert(1)", "Open", "Repo",
                "data:text/html;base64,PHNjcmlwdD4=", "branch");

        String body = PullRequestServiceImpl.buildEmailBody("https://jira.example.com/browse/TEST-1", issue, entity);

        assertFalse(body.contains("href=\"javascript"));
        assertFalse(body.contains("href=\"data"));
        assertTrue(body.contains("<li><strong>Pull Request:</strong> PR</li>"));
        assertTrue(body.contains("<li><strong>Repository:</strong> Repo</li>"));
    }

    @Test
    public void bodyLinksSafeUrls() {
        Issue issue = issue("TEST-1", "Summary");
        PullRequestEntity entity = entity("PR", "https://example.com/pr/1", "Open", "Repo",
                "https://example.com/repo", "branch");

        String body = PullRequestServiceImpl.buildEmailBody("https://jira.example.com/browse/TEST-1", issue, entity);

        assertTrue(body.contains("<a href=\"https://example.com/pr/1\">PR</a>"));
        assertTrue(body.contains("<a href=\"https://example.com/repo\">Repo</a>"));
        assertTrue(body.contains("<a href=\"https://jira.example.com/browse/TEST-1\">TEST-1</a>"));
    }

    @Test
    public void bodyRendersNullFieldsAsEmptyNotLiteralNull() {
        Issue issue = issue("TEST-1", null);
        PullRequestEntity entity = entity("PR", "https://example.com/pr/1", null, null, null, null);

        String body = PullRequestServiceImpl.buildEmailBody("https://jira.example.com/browse/TEST-1", issue, entity);

        assertFalse(body.contains("null"));
        assertTrue(body.contains("<li><strong>Status:</strong> </li>"));
    }

    @Test
    public void bodyEscapesQuotesInsideHrefAttributes() {
        // SafeUrls accepts quote-bearing http(s) URLs, so escapeHtml inside
        // linkOrText is the ONLY guard against href attribute breakout.
        Issue issue = issue("TEST-1", "Summary");
        PullRequestEntity entity = entity("PR", "https://x/\"><script>", "Open", "Repo",
                "https://example.com/repo?q=\"v\"", "branch");

        String body = PullRequestServiceImpl.buildEmailBody("https://jira.example.com/browse/TEST-1", issue, entity);

        assertTrue(body.contains("<a href=\"https://x/&quot;&gt;&lt;script&gt;\">PR</a>"));
        assertTrue(body.contains("<a href=\"https://example.com/repo?q=&quot;v&quot;\">Repo</a>"));
        assertFalse(body.contains("href=\"https://x/\""));
    }

    @Test
    public void linkOrTextEscapesQuotesInsideHref() {
        assertEquals("<a href=\"https://x/&quot;&gt;&lt;script&gt;\">PR</a>",
                PullRequestServiceImpl.linkOrText("https://x/\"><script>", "PR"));
    }

    @Test
    public void subjectRendersNullFieldsAsEmptyNotLiteralNull() {
        Issue issue = issue("TEST-1", null);
        PullRequestEntity entity = entity(null, "https://example.com/pr/1", null, null, null, null);

        String subject = PullRequestServiceImpl.buildEmailSubject(issue, entity);

        assertFalse(subject.contains("null"));
        assertEquals("(TEST-1: ) []  - ", subject);
    }

    @Test
    public void subjectCollapsesHeaderInjectionNewlines() {
        Issue issue = issue("TEST-1", "line1\r\nBcc: victim@example.com");
        PullRequestEntity entity = entity("PR", "https://example.com/pr/1", "Open", "Repo",
                "https://example.com/repo", "branch");

        String subject = PullRequestServiceImpl.buildEmailSubject(issue, entity);

        assertFalse(subject.contains("\r"));
        assertFalse(subject.contains("\n"));
        assertTrue(subject.contains("line1 Bcc: victim@example.com"));
    }

    @Test
    public void escapeHtmlCoversTheFiveSignificantCharacters() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;", PullRequestServiceImpl.escapeHtml("&<>\"'"));
        assertEquals("", PullRequestServiceImpl.escapeHtml(null));
        assertEquals("plain text", PullRequestServiceImpl.escapeHtml("plain text"));
    }

    @Test
    public void linkOrTextFallsBackToTextForUnsafeUrls() {
        assertEquals("PR", PullRequestServiceImpl.linkOrText("javascript:alert(1)", "PR"));
        assertEquals("<a href=\"http://x\">PR</a>", PullRequestServiceImpl.linkOrText("http://x", "PR"));
    }
}
