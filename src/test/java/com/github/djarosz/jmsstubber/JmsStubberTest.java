package com.github.djarosz.jmsstubber;

import com.github.djarosz.jmsstubber.handler.ForwardingHandler;
import com.github.djarosz.jmsstubber.handler.GroovyHandler;
import com.github.djarosz.jmsstubber.handler.LoggeringHandler;
import com.github.djarosz.jmsstubber.handler.MessageCollectingHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Test;

import javax.jms.*;
import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class JmsStubberTest {

  @Test
  public void shouldStartStubber() throws Exception {
    MessageCollectingHandler<TextMessage> messageStore = new MessageCollectingHandler<>();
    JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
        .withBrokerName("jms-stubber")
        .withQueues()
          .withCommonMessageHandler(LoggeringHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("out")
          .withQueue("in", new ForwardingHandler("out"))
        .build();

    stubber.start();

    Connection connection = vmConnectionFactory().createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    Queue inQueue = session.createQueue("in");
    Queue outQueue = session.createQueue("out");

    MessageProducer producer = session.createProducer(inQueue);
    String text = "text to be sent to destinations for testing";
    producer.send(inQueue, session.createTextMessage(text));

    MessageConsumer outConsumer = session.createConsumer(outQueue);
    MessageConsumer inConsumer = session.createConsumer(inQueue);

    TextMessage receivedByOut = (TextMessage) outConsumer.receive();
    TextMessage receivedByIn = (TextMessage) inConsumer.receive();

    assertThat(receivedByIn.getText()).isEqualTo(text);
    assertThat(receivedByOut.getText()).isEqualTo(text);
    assertThat(receivedByOut.getJMSMessageID()).isNotEqualTo(receivedByIn.getJMSMessageID());

    assertThat(messageStore.received("in")).hasSize(1);
    assertThat(messageStore.received("out")).hasSize(1);

    receivedByIn = messageStore.received("in").get(0);
    receivedByOut = messageStore.received("out").get(0);

    assertThat(receivedByIn.getText()).isEqualTo(text);
    assertThat(receivedByOut.getText()).isEqualTo(text);
    assertThat(receivedByOut.getJMSMessageID()).isNotEqualTo(receivedByIn.getJMSMessageID());

    log.info("Closing session.");
    session.close();
    connection.stop();
    stubber.stop();
  }

  @Test
  public void shouldEvaluateGroovyHandler() throws Exception {
    GroovyHandler groovyHandler = new GroovyHandler(new File("target/test-classes/test-handler.groovy"));
    MessageCollectingHandler<TextMessage> messageStore = new MessageCollectingHandler<>();
    JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
        .withBrokerName("jms-stubber")
        .withQueues()
          .withCommonMessageHandler(LoggeringHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("out")
          .withQueue("in", groovyHandler)
        .build();

    stubber.start();

    Connection connection = vmConnectionFactory().createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    Queue inQueue = session.createQueue("in");
    Queue outQueue = session.createQueue("out");

    MessageProducer producer = session.createProducer(inQueue);
    String xml = "<parent><child id='0'>c1</child><child id='1'>c2</child></parent>";
    producer.send(inQueue, session.createTextMessage(xml));

    MessageConsumer outConsumer = session.createConsumer(outQueue);

    TextMessage response = (TextMessage) outConsumer.receive();

    assertThat(response.getText().replaceAll("( |\\n)", "").toLowerCase()).isEqualTo(xml.replaceAll(" ", ""));

    log.info("Closing session.");
    session.close();
    connection.stop();
    stubber.stop();
  }

  @Test
  public void shouldEvaluateGroovyDefaultHandler() throws Exception {
    GroovyHandler groovyHandler = new GroovyHandler(new File("target/test-classes"));
    MessageCollectingHandler<TextMessage> messageStore = new MessageCollectingHandler<>();
    JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
        .withQueues()
          .withCommonMessageHandler(LoggeringHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("test.queue.out")
          .withQueue("test.queue.in", groovyHandler)
        .build();

    stubber.start();

    Connection connection = vmConnectionFactory().createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    sendMessage(session, "test.queue.in", "ignored");
    waitMessageReceived(session, "test.queue.out");

    assertThat(messageStore.received("test.queue.out")).hasSize(1);
    TextMessage response = messageStore.received("test.queue.out").get(0);
    assertThat(response.getText()).isEqualTo("default response");

    log.info("Closing session.");
    session.close();
    connection.stop();
    stubber.stop();
  }

  @Test
  public void shouldEvaluateQueueSpecificGroovyHandler() throws Exception {
    GroovyHandler groovyHandler = new GroovyHandler(new File("target/test-classes"));
    MessageCollectingHandler<TextMessage> messageStore = new MessageCollectingHandler<>();
    JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
        .withQueues()
          .withCommonMessageHandler(LoggeringHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("my.queue.out")
          .withQueue("my.queue.in", groovyHandler)
        .build();

    stubber.start();

    Connection connection = vmConnectionFactory().createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    sendMessage(session, "my.queue.in", "ignored");
    waitMessageReceived(session, "my.queue.out");

    assertThat(messageStore.received("my.queue.out")).hasSize(1);
    TextMessage response = messageStore.received("my.queue.out").get(0);
    assertThat(response.getText()).isEqualTo("my.queue.out response");

    log.info("Closing session.");
    session.close();
    connection.stop();
    stubber.stop();
  }

  @Test
  public void shouldSendMessageUsingTcp() throws Exception {
    String tcpConnector = "tcp://localhost:5678";
    JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
        .withConnectorUri(tcpConnector)
        .withQueues()
          .withCommonMessageHandler(LoggeringHandler.INSTANCE)
          .withQueue("in")
        .build();

    stubber.start();

    Connection connection = connectionFactory(tcpConnector).createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    sendMessage(session, "in", "12345");
    TextMessage received = waitMessageReceived(session, "in");

    assertThat(received.getText()).isEqualTo("12345");
    assertThat(received.getBooleanProperty(JmsStubber.STUBBER_PROCESSED_HEADER)).isTrue();

    log.info("Closing session.");
    session.close();
    connection.stop();
    stubber.stop();
  }

  private void sendMessage(Session session, String queueName, String text) throws JMSException {
    Queue queue = session.createQueue(queueName);
    MessageProducer producer = session.createProducer(queue);
    producer.send(queue, session.createTextMessage(text));
    producer.close();
  }

  private <T extends Message> T waitMessageReceived(Session session, String queueName) throws JMSException {
    return (T) session.createConsumer(session.createQueue(queueName)).receive();
  }

  private ConnectionFactory vmConnectionFactory() {
    return new ActiveMQConnectionFactory("vm://jms-stubber");
  }

  private ConnectionFactory connectionFactory(String uri) {
    return new ActiveMQConnectionFactory(uri);
  }

}