package com.github.djarosz.jmsstubber.handler;

import com.github.djarosz.jmsstubber.HandlerSession;
import com.github.djarosz.jmsstubber.MessageHandler;
import javax.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Logs message received message attributes.
 */
@Slf4j
public class LoggingHandler implements MessageHandler<Message> {

  public static final LoggingHandler INSTANCE = new LoggingHandler();

  @Override
  public void handle(HandlerSession session, Message message) throws Throwable {
    log.info("[{}]: Received: {}", message.getJMSDestination(),
        ToStringBuilder.reflectionToString(message, ToStringStyle.MULTI_LINE_STYLE));
  }
}
