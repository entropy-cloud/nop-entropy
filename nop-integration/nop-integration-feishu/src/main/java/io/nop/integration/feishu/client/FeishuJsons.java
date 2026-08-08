package io.nop.integration.feishu.client;

/**
 * Minimal, dependency-free JSON string/number field extractor used by the
 * Feishu client and HTTP API to pull scalar values out of Feishu JSON
 * responses and event payloads.
 *
 * <p>This deliberately avoids {@code io.nop.api.core.json.JSON}, which needs a
 * registered {@code IJsonProvider} (set up by {@code nop-core} at app
 * startup). Keeping the extractor dependency-free lets the codec/client be
 * unit-tested in isolation and keeps the module self-contained.
 *
 * <p>Only handles flat top-level string and long fields — sufficient for the
 * scalar values Feishu returns for tokens, gateway URLs and the simple
 * inbound-message fields. Full event-shape deserialization is Plan 7's job.
 */
final class FeishuJsons {

    private FeishuJsons() {
    }

    static String extractString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
            j++;
        }
        if (j >= json.length() || json.charAt(j) != '"') {
            return null;
        }
        j++;
        StringBuilder sb = new StringBuilder();
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                char n = json.charAt(j + 1);
                switch (n) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    default:
                        sb.append(n);
                }
                j += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                j++;
            }
        }
        return sb.toString();
    }

    static long extractLong(String json, String key, long defaultValue) {
        if (json == null || key == null) {
            return defaultValue;
        }
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return defaultValue;
        }
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) {
            return defaultValue;
        }
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
            j++;
        }
        int start = j;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) {
            j++;
        }
        if (start == j) {
            return defaultValue;
        }
        try {
            return Long.parseLong(json.substring(start, j));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
