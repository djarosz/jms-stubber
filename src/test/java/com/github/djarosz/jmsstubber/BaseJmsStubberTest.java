package com.github.djarosz.jmsstubber;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class BaseJmsStubberTest {

  void sendMessage(Session session, String queueName, String text) throws JMSException {
    Queue queue = session.createQueue(queueName);
    MessageProducer producer = session.createProducer(queue);
    log.debug("Sending message '{}' to queue: {}", text, queueName);
    producer.send(queue, session.createTextMessage(text));
    log.debug("Sent message '{}' to queue: {}", text, queueName);
    producer.close();
  }

  <T extends Message> T waitMessageReceived(Session session, String queueName) throws JMSException {
    return (T) session.createConsumer(session.createQueue(queueName)).receive();
  }

}