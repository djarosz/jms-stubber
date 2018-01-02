package com.github.djarosz.jmsstubber;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.command.ActiveMQQueue;

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
    setAdvisorySupport(true);
    addConnector("vm://" + brokerName + "?broker.persistent=false");

    PolicyMap policyMap = new PolicyMap();
    ArrayList<PolicyEntry> entries = new ArrayList<>();
    PolicyEntry entry = new PolicyEntry();
    entry.setDestination(new ActiveMQQueue(">"));
    entry.setAdvisoryForDiscardingMessages(true);
    entry.setSendAdvisoryIfNoConsumers(true);
    policyMap.setPolicyEntries(entries);

    setDestinationPolicy(policyMap);

    if (connectorUris != null) {
      for (URI uri : connectorUris) {
        addConnector(uri);
      }
    }
  }

}
