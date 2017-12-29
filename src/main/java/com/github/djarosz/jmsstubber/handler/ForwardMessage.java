package com.github.djarosz.jmsstubber.handler;

import com.github.djarosz.jmsstubber.HandlerSession;
import com.github.djarosz.jmsstubber.MessageHandler;
import com.github.djarosz.jmsstubber.MessageUtils;
import javax.jms.Message;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ForwardMessage implements MessageHandler<Message> {

  @NonNull
  private String forwardTo;

  @Override
  public void handle(HandlerSession session, Message message) throws Throwable {
    log.debug("[{}]: Forwarding message to: {}", message.getJMSDestination(), forwardTo);
    Message copy = MessageUtils.createCopy(session.getJmsSession(), message);
    session.send(forwardTo, copy);
  }
}
