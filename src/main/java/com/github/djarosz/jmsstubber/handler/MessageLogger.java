package com.github.djarosz.jmsstubber.handler;

import com.github.djarosz.jmsstubber.HandlerSession;
import com.github.djarosz.jmsstubber.MessageHandler;
import javax.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Slf4j
public class MessageLogger implements MessageHandler<Message> {

  public static final MessageLogger INSTANCE = new MessageLogger();

  @Override
  public void handle(HandlerSession session, Message message) throws Throwable {
    log.info("[{}]: Received message: {}", message.getJMSDestination(),
        ToStringBuilder.reflectionToString(message, ToStringStyle.MULTI_LINE_STYLE));
  }
}
