package com.github.djarosz.jmsstubber.handler;

import com.github.djarosz.jmsstubber.HandlerSession;
import com.github.djarosz.jmsstubber.MessageHandler;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.jms.Message;
import org.apache.activemq.command.ActiveMQDestination;

public class MessageStoreHandler<T extends Message> implements MessageHandler<T> {

  private Map<String, List<T>> receivedByQueue = new HashMap<>();

  @Override
  public void handle(HandlerSession session, T message) throws Throwable {
    ActiveMQDestination destination = (ActiveMQDestination) message.getJMSDestination();
    String destinationName = destination.getPhysicalName();
    if (!receivedByQueue.containsKey(destinationName)) {
      receivedByQueue.put(destinationName, new LinkedList<>());
    }

    receivedByQueue.get(destinationName).add(message);
  }

  public List<T> received(String destinationName) {
    return receivedByQueue.get(destinationName);
  }

  public List<T> eceivedAll() {
    return receivedByQueue.values().stream()
        .reduce(new LinkedList<>(), (acc, received) -> {acc.addAll(received); return acc;});
  }

}
