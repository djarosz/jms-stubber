package com.github.djarosz.jmsstubber.handler;

import javax.jms.Destination;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQTopic;

/**
 * Forwards copy of the message to specified destination topic.
 */
@Slf4j
public class TopicForwardingHandler extends ForwardingHandler {

  public TopicForwardingHandler(String forwardTo) {
    super(forwardTo);
  }

  @Override
  protected Destination getDestination(String forwardTo) {
    return new ActiveMQTopic(forwardTo);
  }

}
