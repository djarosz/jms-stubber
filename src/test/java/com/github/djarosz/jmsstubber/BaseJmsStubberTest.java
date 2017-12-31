package com.github.djarosz.jmsstubber;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

abstract class BaseJmsStubberTest {

  void sendMessage(Session session, String queueName, String text) throws JMSException {
    Queue queue = session.createQueue(queueName);
    MessageProducer producer = session.createProducer(queue);
    producer.send(queue, session.createTextMessage(text));
    producer.close();
  }

  <T extends Message> T waitMessageReceived(Session session, String queueName) throws JMSException {
    return (T) session.createConsumer(session.createQueue(queueName)).receive();
  }

}