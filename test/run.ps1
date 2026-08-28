$repo = "D:\sofware_install\install\mvn_repo"
$amqp = "$repo\com\rabbitmq\amqp-client\5.19.0\amqp-client-5.19.0.jar"
$slf4j = "$repo\org\slf4j\slf4j-api\2.0.13\slf4j-api-2.0.13.jar"

javac -cp $amqp MqPublish.java
if ($?) { java -cp ".;$amqp;$slf4j" MqPublish }
