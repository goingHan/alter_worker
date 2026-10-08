package com.alertworker.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("node_registry")
public class NodeRegistry extends BaseEntity {
    private String nodeId;
    private String nodeType;
    private String host;
    private Integer port;
    private String status;
    private Integer weight;
    private LocalDateTime lastHeartbeatAt;
}
