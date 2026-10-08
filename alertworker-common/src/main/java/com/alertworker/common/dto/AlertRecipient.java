package com.alertworker.common.dto;

import com.alertworker.common.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecipient implements Serializable {
    private Long assigneeId;
    private String cnName;
    private String phone;
    private String dingdingUsername;
    private String email;
    private ChannelType channelType;
    private Long messageChannelId;
}
