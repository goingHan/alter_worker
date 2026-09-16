package com.alertworker.common.dto;

import lombok.Data;

// #AI-CODE-FILE-dev_release/v0.0.1-20260916: rule->channel binding view for rule table and edit modal
@Data
public class RuleChannelView {
    private Long ruleId;
    private Long channelId;
    private String channelName;
    private String channelType;
}
