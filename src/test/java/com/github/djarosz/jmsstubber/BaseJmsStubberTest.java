package com.github.djarosz.jmsstubber;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;

public abstract class BaseJmsStubberTest {

  protected void sendMessage(Session session, String queueName, String text) throws JMSException {
    Queue queue = session.createQueue(queueName);
    MessageProducer producer = session.createProducer(queue);
    producer.send(queue, session.createTextMessage(text));
    producer.close();
  }

  protected <T extends Message> T waitMessageReceived(Session session, String queueName) throws JMSException {
    return (T) session.createConsumer(session.createQueue(queueName)).receive();
  }

  protected ConnectionFactory vmConnectionFactory() {
    return new ActiveMQConnectionFactory("vm://jms-stubber");
  }

  protected ConnectionFactory connectionFactory(String uri) {
    return new ActiveMQConnectionFactory(uri);
  }

}