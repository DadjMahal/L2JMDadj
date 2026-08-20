package com.aiplayer.behavior.social;

/** MODE: PARTIAL. Compiles and follows the reviewed patterns, but not independently re-verified line-by-line this session — treat as unverified until it passes mvn test. */

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Filters incoming and outgoing chat for:
 * - GM / admin impersonation detection
 * - Phishing / adena scam detection
 * - Server rule violations (auto-decline)
 * - Message normalization
 */
public final class ChatFilter {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://|www\\.)[\\w\\-\\.]+\\.[a-zA-Z]{2,}[/\\w\\-\\.\\?&=]*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ADENA_SCAM_PATTERN = Pattern.compile(
            "\\b(buy|sell)\\b.*\\b(adena|adena)\\b.*\\b(cheap|discount|fast|safe)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> GM_KEYWORDS = new HashSet<>();
    static {
        GM_KEYWORDS.add("[gm]");
        GM_KEYWORDS.add("[admin]");
        GM_KEYWORDS.add("[dev]");
        GM_KEYWORDS.add("server admin");
        GM_KEYWORDS.add("game master");
    }

    private static final Set<String> BANNED_WORDS = new HashSet<>();
    static {
        // Auto-filter these to avoid server mutes/bans
        BANNED_WORDS.add("nigger");
        BANNED_WORDS.add("faggot");
        BANNED_WORDS.add("hitler");
        // Add other server-specific banned terms
    }

    private final String accountName;

    public ChatFilter(String accountName) {
        this.accountName = accountName;
    }

    /**
     * Check if incoming message should be completely ignored.
     */
    public boolean shouldIgnore(String sender, String text) {
        if (text == null) return true;

        // Ignore messages with URLs (phishing risk)
        if (URL_PATTERN.matcher(text).find()) {
            return true;
        }

        // Ignore obvious adena sellers
        if (ADENA_SCAM_PATTERN.matcher(text).find()) {
            return true;
        }

        // Ignore GM impersonators
        String lower = text.toLowerCase();
        for (String gm : GM_KEYWORDS) {
            if (lower.contains(gm)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Normalize outgoing text to avoid detection patterns.
     */
    public String normalizeOutgoing(String text) {
        if (text == null) return null;

        // Prevent accidental banned word usage
        String normalized = text;
        for (String banned : BANNED_WORDS) {
            normalized = normalized.replaceAll("(?i)" + banned, "***");
        }

        return normalized;
    }

    /**
     * Check if sender is pretending to be staff.
     */
    public boolean isImpersonatingStaff(String sender, String text) {
        String lowerSender = sender.toLowerCase();
        return lowerSender.contains("gm ") || lowerSender.contains("admin")
                || lowerSender.startsWith("[gm]");
    }
}
