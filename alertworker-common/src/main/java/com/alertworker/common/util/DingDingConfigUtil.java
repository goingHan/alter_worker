package com.alertworker.common.util;

import java.util.List;

// #AI-CODE-FILE-dev_release/v0.0.1-20260826: DingDing keyword handling shared by worker send path and gateway channel test
public final class DingDingConfigUtil {

    private DingDingConfigUtil() {
    }

    public static String buildMessageContent(Object keywordCfg, String content, String msgType) {
        String keywords = resolveKeywords(keywordCfg);
        if (keywords == null || keywords.isEmpty()) {
            return content;
        }
        if ("markdown".equals(msgType) || "action_card".equals(msgType)) {
            // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260908: append keyword onto the trailing h6 meta line
            // (Alert ID footer) so both stay on one line and save vertical space
            String trimmed = content.stripTrailing();
            if (lastLine(trimmed).startsWith("###### ")) {
                return trimmed + " " + keywords;
            }
            return content + "\n\n###### " + keywords;
        }
        return content + "\n\n" + keywords;
    }

    private static String lastLine(String text) {
        int idx = text.lastIndexOf('\n');
        return idx >= 0 ? text.substring(idx + 1) : text;
    }

    public static String resolveKeywords(Object keywordCfg) {
        if (keywordCfg instanceof String s && !s.isEmpty()) {
            return s;
        }
        if (keywordCfg instanceof List<?> list && !list.isEmpty()) {
            return String.join(" ", list.stream().map(Object::toString).toList());
        }
        return null;
    }
}
