#!/bin/bash
set -e

# URL = 工程前缀 /pw/alertworker + 模块 context-path（gateway=/gw，datamanager=/dm）
BASE_URL="http://localhost:8081/pw/alertworker/gw"
DM_URL="http://localhost:8083/pw/alertworker/dm"

echo "=== AlertWorker E2E Test ==="

# 1. Create API Key
echo "Creating API key..."
API_KEY_RESP=$(curl -s -X POST "$DM_URL/api/v1/api-keys" \
  -H "Content-Type: application/json" \
  -d '{"name":"e2e-test","apiValue":"e2e-test-key-123","rateLimit":100}')
API_KEY_ID=$(echo "$API_KEY_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "API Key ID: $API_KEY_ID"

# 2. Create Message Channel (DingDing)
echo "Creating message channel..."
CHANNEL_RESP=$(curl -s -X POST "$DM_URL/api/v1/channels" \
  -H "Content-Type: application/json" \
  -d '{"name":"e2e-dingding","channelType":"DINGDING","configJson":"{\"webhookUrl\":\"https://oapi.dingtalk.com/robot/send?access_token=test\",\"msgType\":\"text\"}"}')
CHANNEL_ID=$(echo "$CHANNEL_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Channel ID: $CHANNEL_ID"

# 3. Create Assignee
echo "Creating assignee..."
ASSIGNEE_RESP=$(curl -s -X POST "$DM_URL/api/v1/assignees" \
  -H "Content-Type: application/json" \
  -d '{"cnName":"E2E Tester","phone":"13800138000","dingdingUsername":"e2e_tester"}')
ASSIGNEE_ID=$(echo "$ASSIGNEE_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Assignee ID: $ASSIGNEE_ID"

# 4. Create Communication Group
echo "Creating communication group..."
GROUP_RESP=$(curl -s -X POST "$DM_URL/api/v1/groups" \
  -H "Content-Type: application/json" \
  -d '{"name":"e2e-group","sendType":"ALL"}')
GROUP_ID=$(echo "$GROUP_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Group ID: $GROUP_ID"

# 5. Add assignee to group
echo "Adding assignee to group..."
curl -s -X POST "$DM_URL/api/v1/groups/$GROUP_ID/assignees" \
  -H "Content-Type: application/json" \
  -d "{\"assigneeId\":$ASSIGNEE_ID,\"channelType\":\"DINGDING\",\"serviceTypes\":\"\"}"

# 6. Create Message Template
echo "Creating message template..."
TEMPLATE_RESP=$(curl -s -X POST "$DM_URL/api/v1/templates" \
  -H "Content-Type: application/json" \
  -d '{"name":"e2e-template","channelType":"DINGDING","content":"Alert: {{ title }} - {{ content }}","isDefault":true}')
TEMPLATE_ID=$(echo "$TEMPLATE_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Template ID: $TEMPLATE_ID"

# 7. Create Alert Rule
echo "Creating alert rule..."
RULE_RESP=$(curl -s -X POST "$DM_URL/api/v1/rules" \
  -H "Content-Type: application/json" \
  -d '{"name":"e2e-rule","alertType":"JSON","matchKey":"service_type","matchValue":"e2e","retryMax":3,"retryBackoff":"5,10,20"}')
RULE_ID=$(echo "$RULE_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Rule ID: $RULE_ID"

# 8. Link group and template to rule
echo "Linking group and template to rule..."
curl -s -X POST "$DM_URL/api/v1/rules/$RULE_ID/groups" \
  -H "Content-Type: application/json" \
  -d "{\"groupId\":$GROUP_ID}"

curl -s -X POST "$DM_URL/api/v1/rules/$RULE_ID/templates" \
  -H "Content-Type: application/json" \
  -d "{\"templateId\":$TEMPLATE_ID,\"channelType\":\"DINGDING\"}"

# 9. Send test alert
echo "Sending test alert..."
ALERT_RESP=$(curl -s -X POST "$BASE_URL/api/v1/alerts/json" \
  -H "X-API-Key: e2e-test-key-123" \
  -H "Content-Type: application/json" \
  -d '{"service_type":"e2e","title":"E2E Test","content":"Hello from AlertWorker"}')
echo "Alert response: $ALERT_RESP"

echo "=== E2E Test Setup Complete ==="
echo "Check RabbitMQ worker logs and alert history for delivery status."
