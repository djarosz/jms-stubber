package com.github.djarosz.jmsstubber;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;

@Slf4j
public class JmsStubberBuilder {

  private DestinationConfig.DestinationConfigBuilder configBuilder = DestinationConfig.builder();

  private ActiveMQConnectionFactory connectionFactory;

  private InMemoryBrokerService embeddedBrokerService;

  private JmsStubberBuilder() {
  }

  private JmsStubberBuilder(ActiveMQConnectionFactory connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  public class EmbeddedBrokerBuilder {
    private String brokerName = "jms-stubber-" + UUID.randomUUID().toString();
    private List<URI> connectorUris = new ArrayList<>();

    public EmbeddedBrokerBuilder withBrokerName(String brokerName) {
      this.brokerName = brokerName;
      return this;
    }

    @SneakyThrows
    public EmbeddedBrokerBuilder withConnectorUri(String connectorUri) {
      return withConnectorUri(new URI(connectorUri));
    }

    public EmbeddedBrokerBuilder withConnectorUri(URI connectorUri) {
      this.connectorUris.add(connectorUri);
      return this;
    }

    public EmbeddedBrokerBuilder registerCommonHandlersOnAllQueues() {
      configBuilder.registerCommonHandlersOnAllQueues(true);
      return this;
    }

    @SneakyThrows
    public JmsStubberBuilder withQueues() {
      embeddedBrokerService = new InMemoryBrokerService(brokerName, connectorUris);
      return JmsStubberBuilder.this;
    }
  }

  public JmsStubberBuilder withQueues() {
    return this;
  }

  public static JmsStubberBuilder amqConnectionFactory(ActiveMQConnectionFactory connectionFactory) {
    return new JmsStubberBuilder(connectionFactory);
  }

  public static EmbeddedBrokerBuilder embeddedBroker() {
    return new JmsStubberBuilder().embeddedBrokerBuilder();
  }

  private EmbeddedBrokerBuilder embeddedBrokerBuilder() {
    return new EmbeddedBrokerBuilder();
  }

  public JmsStubberBuilder registerCommonHandlersOnAllQueueus() {
    configBuilder.registerCommonHandlersOnAllQueues(true);
    return this;
  }

  public JmsStubberBuilder withQueue(String name, MessageHandler... handlers) {
    configBuilder.addQueue(new QueueConfig(name, handlers));
    return this;
  }

  public JmsStubberBuilder withCommonMessageHandler(MessageHandler handler) {
    return withCommonMessageHandlers(handler);
  }

  public JmsStubberBuilder withCommonMessageHandlers(MessageHandler... handlers) {
    if (handlers != null) {
      Stream.of(handlers).forEach(configBuilder::addCommonMessageHandler);
    }
    return this;
  }

  @SneakyThrows
  public JmsStubber build() {
    JmsStubber jmsStubber = embeddedBrokerService != null
        ? new JmsStubberWithManagedBroker(embeddedBrokerService, configBuilder.build())
        : new DefaultJmsStubber(connectionFactory, configBuilder.build());
    return jmsStubber;
  }

}
