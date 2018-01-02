package com.github.djarosz.jmsstubber;

import javax.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;

@Slf4j
public class JmsStubberWithManagedBroker extends DefaultJmsStubber {

  private BrokerService broker;

  @SneakyThrows
  public JmsStubberWithManagedBroker(BrokerService broker, DestinationConfig config) {
    super(new ActiveMQConnectionFactory(broker.getVmConnectorURI()), config);
    this.broker = broker;
  }

  @Override
  @PostConstruct
  @SneakyThrows
  public void start() {
    log.info("Starting embedded broker");
    broker.start();
    super.start();
  }

  @Override
  @PostConstruct
  @SneakyThrows
  public void stop() {
    super.stop();
    log.info("Stopping embedded broker");
    broker.stop();
  }

}
