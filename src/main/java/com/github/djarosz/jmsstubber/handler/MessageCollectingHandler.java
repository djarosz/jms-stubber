package com.github.djarosz.jmsstubber.handler;

import com.github.djarosz.jmsstubber.HandlerSession;
import com.github.djarosz.jmsstubber.MessageHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.jms.Message;
import org.apache.activemq.command.ActiveMQDestination;

/**
 * Stores every received message.
 */
public class MessageCollectingHandler<T extends Message> implements MessageHandler<T> {

  private Map<String, List<T>> receivedByQueue = Collections.synchronizedMap(new HashMap<>());

  @Override
  public void handle(HandlerSession session, T message) throws Throwable {
    ActiveMQDestination destination = (ActiveMQDestination) message.getJMSDestination();
    String destinationName = destination.getPhysicalName();
    if (!receivedByQueue.containsKey(destinationName)) {
      receivedByQueue.put(destinationName, Collections.synchronizedList(new LinkedList<>()));
    }

    receivedByQueue.get(destinationName).add(message);
  }

  /**
   * Returns list of messages received on specified queue or topic
   */
  public List<T> received(String destinationName) {
    return receivedByQueue.get(destinationName);
  }

  /**
   * Returns all received messages on any destination.
   */
  public List<T> eceivedAll() {
    return receivedByQueue.values().stream()
        .reduce(new LinkedList<>(), (acc, received) -> {
          acc.addAll(received);
          return acc;
        });
  }

}
