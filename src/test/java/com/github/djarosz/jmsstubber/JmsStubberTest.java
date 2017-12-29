package com.github.djarosz.jmsstubber;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.djarosz.jmsstubber.handler.ForwardMessage;
import com.github.djarosz.jmsstubber.handler.MessageLogger;
import com.github.djarosz.jmsstubber.handler.MessageStoreHandler;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
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
    MessageStoreHandler<TextMessage> messageStore = new MessageStoreHandler<>();
    JmsStubberBuilder.forEmbeddedBroker()
        .withBrokerName("jms-stubber")
        .destinationConfig()
          .withCommonMessageHandler(MessageLogger.INSTANCE)
          .withCommonMessageHandler(messageStore)
          .withQueue("out")
          .withQueue("in", new ForwardMessage("out"))
        .build()
        .initialize();

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
  }

  @Test
  public void shouldEvaluateGroovyHandler() throws Exception {
//    GroovyHandler groovyHandler = new GroovyHandler(new File("target/test-classes/test-handler.groovy"));
//    DestinationConfig config = DestinationConfig.builder()
//        .name("myjms")
//        .addQueueConfig(QueueConfig.builder()
//            .name("out")
//            .build())
//        .addQueueConfig(QueueConfig.builder()
//            .name("in")
//            .addMessageListener(new MessageLogger())
//            .addMessageListener(groovyHandler)
//            .build())
//        .build();
//    JmsStubber jmsStubber = new JmsStubber(config);
//
//    jmsStubber.start();
//
//    jmsStubber.send("in", "<root><child>c1</child><child>c2</child></root>");
//    TextMessage msg = jmsStubber.receive("out");
//
//    Thread.sleep(5000L);
//
//    jmsStubber.stop();
  }


}