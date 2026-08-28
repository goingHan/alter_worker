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
            return content + "\n\n###### " + keywords;
        }
        return content + "\n\n" + keywords;
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
