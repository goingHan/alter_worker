package com.alertworker.common.dto;

import com.alertworker.common.enums.MessageKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessagePayload implements Serializable {
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: ALERT | TEST | REPORT; null means ALERT (legacy payloads)
    private MessageKind kind;
    private Long alertMessageId;
    private String messageNo;
    private Long alertRuleId;
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: for REPORT payloads, the daily_report's template id; rendered per channel in the Worker
    private Long reportTemplateId;
    // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261009: REPORT payloads are rendered once by the data-processing module;
    // the Worker only dispatches. `renderedContent` is the rendered body (HTML or text) and `htmlUrl` is the public
    // URL of the uploaded HTML file (null for TEXT templates).
    private String renderedContent;
    private String htmlUrl;
    private Map<String, Object> context;
    private List<AlertRecipient> recipients;
}
