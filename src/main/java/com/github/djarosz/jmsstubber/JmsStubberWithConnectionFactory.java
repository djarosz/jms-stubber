package com.github.djarosz.jmsstubber;

import com.github.djarosz.jmsstubber.util.MessageUtils;
import com.github.djarosz.jmsstubber.util.StreamUtils;
import java.util.Collections;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.command.ActiveMQQueue;

@Slf4j
@RequiredArgsConstructor
public class JmsStubberWithConnectionFactory implements JmsStubber {

  @NonNull
  private ActiveMQConnectionFactory connectionFactory;
  @NonNull
  private DestinationConfig config;

  private ActiveMQConnection stubberConnection;
  private ActiveMQSession stubberSession;

  @Override
  @PostConstruct
  @SneakyThrows
  public void start() {
    log.info("Initializing JMS Stubber...");

    stubberConnection = (ActiveMQConnection) connectionFactory.createConnection();
    stubberConnection.start();
    stubberSession = (ActiveMQSession) stubberConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    createQueues();
    attachMessageHandlersToQueues();

    log.info("Initialized");
  }

  @Override
  @PreDestroy
  @SneakyThrows
  public void stop() {
    log.info("Destroying JMS stubber...");

    stubberSession.close();
    stubberConnection.close();

    log.info("Done");
  }

  private void createQueues() throws JMSException {
    for (QueueConfig queueConfig : config.getQueues()) {
      log.info("Creating queue: {}", queueConfig.getName());
      stubberSession.createQueue(queueConfig.getName());
    }
  }

  private void attachMessageHandlersToQueues() throws JMSException {

    for (QueueConfig queueConfig : config.getQueues()) {
      log.info("Stubbing queue: {}", queueConfig.getName());
      ActiveMQQueue queue = new ActiveMQQueue(queueConfig.getName() + "?consumer.priority=100");

      MessageConsumer consumer = stubberSession.createConsumer(queue, STUBBER_PROCESSED_HEADER + " IS NULL");
      MessageListener handlerChainListener = message -> {
        HandlerSessionImpl handlerSession = new HandlerSessionImpl(stubberConnection);
        StreamUtils.mergeAsStream(
            config.getCommonMessageHandlers(),
            queueConfig.getMessageHandlers(),
            Collections.singleton(MarkMessageAsHandledByJmsStubber.INSTANCE))
            .forEach(executeHandler(handlerSession, message));
        handlerSession.close();
      };
      consumer.setMessageListener(handlerChainListener);
    }
  }

  @SneakyThrows
  private Consumer<MessageHandler> executeHandler(HandlerSession handlerSession, Message message) {
    Destination destination = message.getJMSDestination();
    return handler -> {
      try {
        log.debug("[{}]: Calling handler: {}", destination, handler.getClass().getName());
        handler.handle(handlerSession, message);
      } catch (Throwable e) {
        log.error("[{}]: Error while calling handler.", destination, e);
      }
    };
  }

  private static class MarkMessageAsHandledByJmsStubber implements MessageHandler {
    public static final MarkMessageAsHandledByJmsStubber INSTANCE = new MarkMessageAsHandledByJmsStubber();

    @Override
    public void handle(HandlerSession session, Message message) throws Throwable {
      Message copy = MessageUtils.createCopy(session.getJmsSession(), message);
      copy.setBooleanProperty(STUBBER_PROCESSED_HEADER, true);
      copy.setJMSMessageID(null);
      copy.setJMSDestination(null);
      log.debug("[{}]: Resending message same queue", message.getJMSDestination());
      session.send(message.getJMSDestination(), copy);
    }
  }
}
