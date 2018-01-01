package com.github.djarosz.jmsstubber.handler;

import com.github.djarosz.jmsstubber.HandlerSession;
import com.github.djarosz.jmsstubber.JmsStubber;
import com.github.djarosz.jmsstubber.MessageHandler;
import com.github.djarosz.jmsstubber.util.MessageUtils;
import javax.jms.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResendMessageToOriginDestination implements MessageHandler {

  public static final ResendMessageToOriginDestination INSTANCE = new ResendMessageToOriginDestination();

  @Override
  public void handle(HandlerSession session, Message message) throws Throwable {
    Message copy = MessageUtils.createCopy(session.getJmsSession(), message);

    copy.setBooleanProperty(JmsStubber.STUBBER_PROCESSED_HEADER, true);
    copy.setJMSMessageID(null);
    copy.setJMSDestination(null);

    log.debug("[{}]: Resending message to the same queue", message.getJMSDestination());

    session.send(message.getJMSDestination(), copy);
  }
}
