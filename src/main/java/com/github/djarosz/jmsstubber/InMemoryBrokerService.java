package com.github.djarosz.jmsstubber;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.BrokerService;

@Slf4j
public class InMemoryBrokerService extends BrokerService {

  public InMemoryBrokerService(String brokerName, URI... connectorUris) {
    this(brokerName, connectorUris == null ? null : Arrays.asList(connectorUris));
  }

  /**
   * Creates ActiveMQ broker preconfigured for non persistent (in-memory) usage.
   */
  @SneakyThrows
  public InMemoryBrokerService(String brokerName, Collection<URI> connectorUris) {
    setPersistent(false);
    setBrokerName(brokerName);
    setUseJmx(false);
    setStartAsync(false);
    addConnector("vm://" + brokerName + "?broker.persistent=false");

    if (connectorUris != null) {
      for (URI uri : connectorUris) {
        addConnector(uri);
      }
    }
  }

}
