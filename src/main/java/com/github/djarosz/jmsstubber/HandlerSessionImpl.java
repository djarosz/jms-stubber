package com.github.djarosz.jmsstubber;

import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import lombok.SneakyThrows;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;

public class HandlerSessionImpl implements HandlerSession {

  private ActiveMQConnection amqConnection;
  private Session jmsSession;

  @SneakyThrows
  HandlerSessionImpl(ActiveMQConnection amqConnection) {
    this.amqConnection = amqConnection;
    this.jmsSession = amqConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
  }

  @Override
  @SneakyThrows
  public void sendToQueueWithReplyTo(String queueName, String replyQueueName, String text) {
    TextMessage textMessage = jmsSession.createTextMessage(text);
    textMessage.setJMSReplyTo(getQueue(replyQueueName));
    send(getQueue(queueName), textMessage);
  }

  @Override
  @SneakyThrows
  public void sendToQueue(String queueName, String text) {
    send(getQueue(queueName), text);
  }

  @Override
  public void sendToQueue(String queueName, Message message) {
    send(getQueue(queueName), message);
  }

  @Override
  @SneakyThrows
  public void send(Destination destination, String text) {
    send(destination, jmsSession.createTextMessage(text));
  }

  @Override
  @SneakyThrows
  public void send(Destination destination, Message message) {
    MessageProducer producer = jmsSession.createProducer(destination);
    producer.send(destination, message);
    producer.close();
  }

  @Override
  public Queue getQueue(String queueName) {
    return new ActiveMQQueue(queueName);
  }

  @Override
  public Topic getTopic(String topicName) {
    return new ActiveMQTopic(topicName);
  }

  @Override
  public Session getJmsSession() {
    return jmsSession;
  }

  @SneakyThrows
  void close() {
    jmsSession.close();
  }
}
