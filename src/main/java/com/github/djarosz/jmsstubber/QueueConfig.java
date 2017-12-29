package com.github.djarosz.jmsstubber;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class QueueConfig {
  String name;
  List<MessageHandler> messageHandlers;

  public QueueConfig(String queueName, MessageHandler... handlers) {
    this.name = queueName;
    this.messageHandlers = handlers == null ? Collections.emptyList() : Arrays.asList(handlers);
  }
}
