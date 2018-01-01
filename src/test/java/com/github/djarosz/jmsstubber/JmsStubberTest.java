package com.github.djarosz.jmsstubber;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.djarosz.jmsstubber.handler.ForwardingHandler;
import com.github.djarosz.jmsstubber.handler.GroovyHandler;
import com.github.djarosz.jmsstubber.handler.LoggingHandler;
import com.github.djarosz.jmsstubber.handler.MessageCollectingHandler;
import java.io.File;
import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.TextMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Test;

@Slf4j
public class JmsStubberTest extends BaseJmsStubberTest {

  @Test
  public void shouldAttacheHandlersToDynamicallyCreatedQueues() throws Exception {
    MessageCollectingHandler<TextMessage> messageStore = new MessageCollectingHandler<>();
    JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
        .withQueues()
          .withCommonMessageHandler(LoggingHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withCommonMessageHandler(new ForwardingHandler("out"))
        .build();

    stubber.start();

    Connection connection = stubber.getConnectionFactory().createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    String text = "text to be sent to destinations for testing";
    sendMessage(session, "in", text);

    TextMessage receivedByIn = waitMessageReceived(session, "in");
    TextMessage receivedByOut = waitMessageReceived(session, "out");

    assertThat(receivedByIn.getText()).isEqualTo(text);
    assertThat(receivedByOut.getText()).isEqualTo(text);

    assertThat(messageStore.received("in")).hasSize(1);
    assertThat(messageStore.received("out")).hasSize(1);

    receivedByIn = messageStore.received("in").get(0);
    receivedByOut = messageStore.received("out").get(0);

    assertThat(receivedByIn.getText()).isEqualTo(text);
    assertThat(receivedByOut.getText()).isEqualTo(text);

    session.close();
    connection.stop();
    stubber.stop();
  }

  @Test
  public void shouldStartStubber() throws Exception {
    MessageCollectingHandler<TextMessage> messageStore = new MessageCollectingHandler<>();
    JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
        .withQueues()
          .withCommonMessageHandler(LoggingHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("out")
          .withQueue("in", new ForwardingHandler("out"))
        .build();

    stubber.start();

    Connection connection = stubber.getConnectionFactory().createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    String text = "text to be sent to destinations for testing";
    sendMessage(session, "in", text);

    TextMessage receivedByIn = waitMessageReceived(session, "in");
    TextMessage receivedByOut = waitMessageReceived(session, "out");

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

    session.close();
    connection.stop();
    stubber.stop();
  }

  @Test
  public void shouldEvaluateGroovyHandler() throws Exception {
    GroovyHandler groovyHandler = new GroovyHandler(new File("target/test-classes/test-handler.groovy"));
    MessageCollectingHandler<TextMessage> messageStore = new MessageCollectingHandler<>();
    JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
        .withQueues()
          .withCommonMessageHandler(LoggingHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("out")
          .withQueue("in", groovyHandler)
        .build();

    stubber.start();

    Connection connection = stubber.getConnectionFactory().createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    String xml = "<parent><child id='0'>c1</child><child id='1'>c2</child></parent>";
    sendMessage(session, "in", xml);
    TextMessage response = waitMessageReceived(session, "out");

    assertThat(response.getText().replaceAll("( |\\n)", "").toLowerCase()).isEqualTo(xml.replaceAll(" ", ""));

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
          .withCommonMessageHandler(LoggingHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("test.queue.out")
          .withQueue("test.queue.in", groovyHandler)
        .build();

    stubber.start();

    Connection connection = stubber.getConnectionFactory().createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    sendMessage(session, "test.queue.in", "ignored");
    TextMessage response = waitMessageReceived(session, "test.queue.out");

    assertThat(messageStore.received("test.queue.out")).hasSize(1);
    assertThat(response.getText()).isEqualTo("default response");

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
          .withCommonMessageHandler(LoggingHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("my.queue.out")
          .withQueue("my.queue.in", groovyHandler)
        .build();

    stubber.start();

    Connection connection = stubber.getConnectionFactory().createConnection();
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
          .withCommonMessageHandler(LoggingHandler.INSTANCE)
          .withQueue("in")
        .build();

    stubber.start();

    Connection connection = new ActiveMQConnectionFactory(tcpConnector).createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    sendMessage(session, "in", "12345");
    TextMessage received = waitMessageReceived(session, "in");

    assertThat(received.getText()).isEqualTo("12345");
    assertThat(received.getBooleanProperty(JmsStubber.STUBBER_PROCESSED_HEADER)).isTrue();

    session.close();
    connection.stop();
    stubber.stop();
  }

}