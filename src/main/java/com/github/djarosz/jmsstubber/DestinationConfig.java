package com.github.djarosz.jmsstubber;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

@Builder
@Getter
public class DestinationConfig {

  @Singular("addQueue")
  List<QueueConfig> queues;

  @Singular("addCommonMessageHandler")
  List<MessageHandler> commonMessageHandlers;
}
