package com.github.djarosz.jmsstubber;

import javax.jms.Message;

@FunctionalInterface
public interface MessageHandler<T extends Message> {

  void handle(HandlerSession session, T message) throws Throwable;

}
