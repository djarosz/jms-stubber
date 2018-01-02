package com.github.djarosz.jmsstubber.handler;

import javax.jms.Destination;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQQueue;

/**
 * Forwards copy of the message to specified destination queue.
 */
@Slf4j
public class QueueForwardingHandler extends ForwardingHandler {

  public QueueForwardingHandler(String forwardTo) {
    super(forwardTo);
  }

  @Override
  protected Destination getDestination(String forwardTo) {
    return new ActiveMQQueue(forwardTo);
  }

}
