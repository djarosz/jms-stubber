package com.github.djarosz.jmsstubber;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.BrokerService;

@Slf4j
public class JmsStubberBuilder {

  private DestinationConfig.DestinationConfigBuilder configBuilder = DestinationConfig.builder();

  private BrokerService broker;

  private boolean embeddedBroker;

  private JmsStubberBuilder() {
  }

  private JmsStubberBuilder(BrokerService broker) {
    this.broker = broker;
  }

  public class EmbeddedBrokerBuilder {
    private String brokerName = "jms-stubber";
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

    @SneakyThrows
    public JmsStubberBuilder destinationConfig() {
      embeddedBroker = true;
      broker = new BrokerService();
      broker.setPersistent(false);
      broker.setBrokerName(brokerName);
      broker.setUseJmx(false);
      broker.addConnector("vm://" + brokerName + "?broker.persistent=false");

      for (URI uri : connectorUris) {
        broker.addConnector(uri);
      }

      return JmsStubberBuilder.this;
    }
  }

  public JmsStubberBuilder destinationConfig() {
    return this;
  }

  public static JmsStubberBuilder forBroker(BrokerService broker) {
    return new JmsStubberBuilder(broker);
  }

  public static EmbeddedBrokerBuilder forEmbeddedBroker() {
    return new JmsStubberBuilder().embeddedBrokerBuilder();
  }

  private EmbeddedBrokerBuilder embeddedBrokerBuilder() {
    return new EmbeddedBrokerBuilder();
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
    JmsStubber jmsStubber = new JmsStubber(broker, configBuilder.build());
    if (embeddedBroker) {
      log.info("Starting broker...");
      broker.setStartAsync(false);
      broker.start();
      broker.addShutdownHook(jmsStubber::destroy);
    }
    return jmsStubber;
  }

}
