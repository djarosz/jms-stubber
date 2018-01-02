package com.github.djarosz.jmsstubber;

import com.github.djarosz.jmsstubber.handler.ResendMessageToOriginDestination;
import com.github.djarosz.jmsstubber.util.Streams;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
import org.apache.activemq.advisory.AdvisorySupport;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.command.ProducerInfo;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@Slf4j
@RequiredArgsConstructor
public class DefaultJmsStubber implements JmsStubber, MessageListener {

  @NonNull
  private ActiveMQConnectionFactory connectionFactory;
  @NonNull
  private DestinationConfig config;

  private ActiveMQConnection stubberConnection;
  private ActiveMQSession stubberSession;

  private Map<String, MessageListener> attachdQueueHandlers = new ConcurrentHashMap<>();

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
    stubberSession = (ActiveMQSession) stubberConnection
        .createSession(false, Session.AUTO_ACKNOWLEDGE);

    attachListenersOnDynamicalCreatedQueues();
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

  private void attachListenersOnDynamicalCreatedQueues() {
    ActiveMQQueue anyQueue = new ActiveMQQueue(">");
    Stream.of(
        AdvisorySupport.getConnectionAdvisoryTopic(),
        AdvisorySupport.getDestinationAdvisoryTopic(anyQueue),
        AdvisorySupport.getProducerAdvisoryTopic(anyQueue),
        AdvisorySupport.getConsumerAdvisoryTopic(anyQueue),
        AdvisorySupport.getMessageDLQdAdvisoryTopic(anyQueue),
        AdvisorySupport.getNoConsumersAdvisoryTopic(anyQueue))
        .forEach(attachAdvisoryMessageListener());
  }

  private Consumer<ActiveMQTopic> attachAdvisoryMessageListener() {
    return destination -> {
      try {
        MessageConsumer consumer = stubberSession.createConsumer(destination);
        consumer.setMessageListener(this);
      } catch (JMSException e) {
        log.error("Could not attach advisory message listener to: {}", destination, e);
      }
    };
  }

  @Override
  public void onMessage(Message msg) {
    try {
      ActiveMQMessage aMsg = (ActiveMQMessage) msg;
      ActiveMQDestination dest = null;

      if (aMsg.getDataStructure() instanceof ProducerInfo) {
        ProducerInfo info = (ProducerInfo) aMsg.getDataStructure();
        dest = info.getDestination();
      } else if (aMsg.getDataStructure() instanceof DestinationInfo) {
        DestinationInfo info = (DestinationInfo) aMsg.getDataStructure();
        dest = info.getDestination();
      } else {
        log.debug("aaa: {}", ToStringBuilder.reflectionToString(msg, ToStringStyle.MULTI_LINE_STYLE));
      }
      if (dest != null && dest instanceof ActiveMQQueue) {
        attachMessageHandlerIfNotAttached((ActiveMQQueue) dest);
      }
    } catch (Exception e) {
      log.error("Could not handle advisory message: {}", ToStringBuilder.reflectionToString(msg), e);
    }
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
      attachMessageHandlerIfNotAttached(queueConfig.getName(), queueConfig.getMessageHandlers());
    }
  }

  @SneakyThrows
  private void attachMessageHandlerIfNotAttached(ActiveMQQueue queue) {
    String queueName = queue.getQueueName();

    if (attachdQueueHandlers.containsKey(queueName)) {
      return;
    }

    List<MessageHandler> queueHandlers = config.getQueues().stream()
        .filter(config -> config.getName().equals(queueName))
        .map(QueueConfig::getMessageHandlers)
        .findFirst()
        .orElse(null);

    attachMessageHandlerIfNotAttached(queueName, queueHandlers);
  }

  @SneakyThrows
  private void attachMessageHandlerIfNotAttached(String queueName, List<MessageHandler> queueHandlers) {
    attachdQueueHandlers.computeIfAbsent(queueName, qName -> attachMessageHandler(qName, queueHandlers));
  }

  @SneakyThrows
  private MessageListener attachMessageHandler(String queueName, List<MessageHandler> queueHandlers) {
    log.info("Attaching handlers to queue: {}", queueName);
    ActiveMQQueue queue = new ActiveMQQueue(queueName + "?consumer.priority=100");
    MessageConsumer consumer = stubberSession.createConsumer(queue, STUBBER_PROCESSED_HEADER + " IS NULL");
    MessageListener listener = stubberQueueMessageListener(queueHandlers);
    consumer.setMessageListener(listener);
    return listener;
  }

  private MessageListener stubberQueueMessageListener(List<MessageHandler> queueHandlers) {
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
