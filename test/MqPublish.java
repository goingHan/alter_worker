import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MqPublish {
    public static void main(String[] args) throws Exception {
        String body = "{"
                + "\"alertMessageId\":42,"
                + "\"alertRuleId\":3,"
                + "\"context\":{"
                + "\"title\":\"CPU \u4f7f\u7528\u7387\u8fc7\u9ad8\","
                + "\"content\":\"node-01 CPU \u4f7f\u7528\u7387\u8d85\u8fc7 90%\""
                + "},"
                + "\"recipients\":[{"
                + "\"assigneeId\":1,"
                + "\"cnName\":\"\u5f20\u4e09\","
                + "\"phone\":\"13800138000\","
                + "\"dingdingUsername\":\"zhangsan\","
                + "\"channelType\":\"DINGDING\","
                + "\"messageChannelId\":4"
                + "}]"
                + "}";

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("172.168.1.238");
        factory.setPort(5672);
        factory.setUsername("celery");
        factory.setPassword("123.com");
        factory.setVirtualHost("alter_worker");

        try (Connection connection = factory.newConnection()) {
            Channel channel = connection.createChannel();
            Map<String, Object> headers = new HashMap<>();
            headers.put("__TypeId__", "com.alertworker.common.dto.AlertMessagePayload");
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(2)
                    .headers(headers)
                    .build();
            channel.basicPublish("alertworker.exchange", "alertworker.routing.key", props,
                    body.getBytes(StandardCharsets.UTF_8));
            System.out.println("published: " + body);
        }
    }
}
