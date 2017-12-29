package com.github.djarosz.jmsstubber;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.djarosz.jmsstubber.handler.ForwardingHandler;
import com.github.djarosz.jmsstubber.handler.GroovyHandler;
import com.github.djarosz.jmsstubber.handler.LoggeringHandler;
import com.github.djarosz.jmsstubber.handler.MessageCollectingHandler;
import java.io.File;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Test;

@Slf4j
public class JmsStubberTest {

  private ConnectionFactory connectionFactory() {
    return new ActiveMQConnectionFactory("vm://jms-stubber");
  }

  @Test
  public void shouldStartStubber() throws Exception {
    MessageCollectingHandler<TextMessage> messageStore = new MessageCollectingHandler<>();
    JmsStubber stubber = JmsStubberBuilder.forEmbeddedBroker()
        .withBrokerName("jms-stubber")
        .destinationConfig()
        .withCommonMessageHandler(LoggeringHandler.INSTANCE)
        .withCommonMessageHandler(messageStore)
        .withQueue("out")
        .withQueue("in", new ForwardingHandler("out"))
        .build();

    stubber.start();

    Connection connection = connectionFactory().createConnection();
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
    JmsStubber stubber = JmsStubberBuilder.forEmbeddedBroker()
        .withBrokerName("jms-stubber")
        .destinationConfig()
          .withCommonMessageHandler(LoggeringHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("out")
          .withQueue("in", groovyHandler)
        .build();

    stubber.start();

    Connection connection = connectionFactory().createConnection();
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
    JmsStubber stubber = JmsStubberBuilder.forEmbeddedBroker()
        .destinationConfig()
          .withCommonMessageHandler(LoggeringHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("test.queue.out")
          .withQueue("test.queue.in", groovyHandler)
        .build();

    stubber.start();

    Connection connection = connectionFactory().createConnection();
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
    JmsStubber stubber = JmsStubberBuilder.forEmbeddedBroker()
        .destinationConfig()
          .withCommonMessageHandler(LoggeringHandler.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("my.queue.out")
          .withQueue("my.queue.in", groovyHandler)
        .build();

    stubber.start();

    Connection connection = connectionFactory().createConnection();
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

  private void sendMessage(Session session, String queueName, String text) throws JMSException {
    Queue queue = session.createQueue(queueName);
    MessageProducer producer = session.createProducer(queue);
    producer.send(queue, session.createTextMessage(text));
    producer.close();
  }

  private <T extends Message> T waitMessageReceived(Session session, String queueName) throws JMSException {
    return (T) session.createConsumer(session.createQueue(queueName)).receive();
  }

}