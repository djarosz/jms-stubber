package com.github.djarosz.jmsstubber;

import com.github.djarosz.jmsstubber.handler.ResendMessageToOriginDestination;
import com.github.djarosz.jmsstubber.util.Streams;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jms.ConnectionFactory;
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
import org.apache.commons.lang3.builder.ToStringBuilder;

@Slf4j
@RequiredArgsConstructor
public class DefaultJmsStubber implements JmsStubber, MessageListener {

  @NonNull
  private ActiveMQConnectionFactory connectionFactory;
  @NonNull
  private DestinationConfig config;

  private ActiveMQConnection stubberConnection;
  private ActiveMQSession stubberSession;

  @Override
  public ConnectionFactory getConnectionFactory() {
    return connectionFactory;
  }

  @Override
  @PostConstruct
  @SneakyThrows
  public void start() {
    log.info("Initializing JMS Stubber...");

    stubberConnection = (ActiveMQConnection) connectionFactory.createConnection();
    stubberConnection.start();
    stubberSession = (ActiveMQSession) stubberConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    attachCommonHandlersOnAllQueues();
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

  @SneakyThrows
  private void attachCommonHandlersOnAllQueues() {
    if (config.isRegisterCommonHandlersOnAllQueues()) {
      ActiveMQQueue anyQueue = new ActiveMQQueue(">?consumer.priority=100");
      MessageConsumer consumer = stubberSession.createConsumer(anyQueue, STUBBER_PROCESSED_HEADER + " IS NULL");
      consumer.setMessageListener(this);
    }
  }

  @Override
  public void onMessage(Message msg) {
    try {
      ActiveMQQueue amqQueue = (ActiveMQQueue) msg.getJMSDestination();
      if (!config.getQueues().stream().map(QueueConfig::getQueueName).anyMatch(amqQueue.getQueueName()::equals)) {
        commonMessageListener().onMessage(msg);
      }
    } catch (Exception e) {
      log.error("Could not handle advisory message: {}", ToStringBuilder.reflectionToString(msg), e);
    }
  }

  private void createQueues() throws JMSException {
    for (QueueConfig queueConfig : config.getQueues()) {
      log.info("Creating queue: {}", queueConfig.getQueueName());
      stubberSession.createQueue(queueConfig.getQueueName());
    }
  }

  private void attachMessageHandlersToQueues() throws JMSException {
    for (QueueConfig queueConfig : config.getQueues()) {
      log.info("Stubbing queue: {}", queueConfig.getQueueName());
      attachMessageHandlerIfNotAttached(queueConfig);
    }
  }

  @SneakyThrows
  private void attachMessageHandlerIfNotAttached(QueueConfig queueConfig) {
    String queueName = queueConfig.getQueueName();
    List<MessageHandler> queueHandlers = queueConfig.getMessageHandlers();

    log.info("Attaching handlers to queue: {}", queueName);

    ActiveMQQueue queue = new ActiveMQQueue(queueName + "?consumer.priority=100");
    MessageConsumer consumer = stubberSession.createConsumer(queue, STUBBER_PROCESSED_HEADER + " IS NULL");
    consumer.setMessageListener(perDestinationMessageListener(queueHandlers));
  }

  private MessageListener commonMessageListener() {
    return perDestinationMessageListener(null);
  }

  private MessageListener perDestinationMessageListener(List<MessageHandler> queueHandlers) {
    return message -> {
      HandlerSessionImpl handlerSession = new HandlerSessionImpl(stubberConnection);
      Streams.mergeAsStream(
          config.getCommonMessageHandlers(),
          queueHandlers,
          Collections.singleton(ResendMessageToOriginDestination.INSTANCE))
          .forEach(executeHandler(handlerSession, message));
      handlerSession.close();
    };
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

}
