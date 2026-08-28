import com.rabbitmq.client.*;

public class QueueCheck {
    public static void main(String[] args) throws Exception {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost("172.168.1.238");
        f.setPort(5672);
        f.setUsername("celery");
        f.setPassword("123.com");
        f.setVirtualHost("alter_worker");
        try (Connection c = f.newConnection()) {
            Channel ch = c.createChannel();
            try {
                AMQP.Queue.DeclareOk q = ch.queueDeclarePassive("alertworker.queue");
                System.out.println("QUEUE alertworker.queue: messages=" + q.getMessageCount()
                        + " consumers=" + q.getConsumerCount());
            } catch (Exception e) {
                System.out.println("QUEUE alertworker.queue: ERROR " + e.getMessage());
            }
            try {
                ch.exchangeDeclarePassive("alertworker.exchange");
                System.out.println("EXCHANGE alertworker.exchange: OK");
            } catch (Exception e) {
                System.out.println("EXCHANGE alertworker.exchange: ERROR " + e.getMessage());
            }
            try {
                AMQP.Queue.DeclareOk q2 = ch.queueDeclarePassive("alertworker.dlq");
                System.out.println("QUEUE alertworker.dlq: messages=" + q2.getMessageCount()
                        + " consumers=" + q2.getConsumerCount());
            } catch (Exception e) {
                System.out.println("QUEUE alertworker.dlq: ERROR " + e.getMessage());
            }
        }
    }
}
