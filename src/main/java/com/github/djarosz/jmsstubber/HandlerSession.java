package com.github.djarosz.jmsstubber;

import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

public interface HandlerSession {

  void sendWithReplyTo(String destinationName, String replyDestinationName, String text);

  void send(String destinationName, String text);

  void send(String destinationName, Message message);

  void send(Destination destination, String text);

  void send(Destination destination, Message message);

  Queue getQueue(String queueName);

  Topic getTopic(String topicName);

  Session getJmsSession();

}
