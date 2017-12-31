package com.github.djarosz.jmsstubber;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.junit.Test;

public class JmStubberPropertyConfigurerTest extends BaseJmsStubberTest {

  @Test
  public void shouldParseConfigFromPropertiesAndEvaluateGroovyDefaultHandler() throws Exception {
    Properties props = new Properties();
    props.setProperty("queue.handler.1", "com.github.djarosz.jmsstubber.handler.LoggingHandler");
    props.setProperty("queue.handler.2",
        "com.github.djarosz.jmsstubber.handler.MessageCollectingHandler");
    props.setProperty("queue.out.name", "test.queue.out");
    props.setProperty("queue.in.name", "test.queue.in");
    props.setProperty("queue.in.handler.1",
        "com.github.djarosz.jmsstubber.handler.GroovyHandler,target/test-classes");

    JmsStubber stubber = JmStubberPropertyConfigurer.build(props);
    stubber.start();

    Connection connection = stubber.getConnectionFactory().createConnection();
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    sendMessage(session, "test.queue.in", "ignored");
    TextMessage response  = waitMessageReceived(session, "test.queue.out");

    assertThat(response.getText()).isEqualTo("default response");

    session.close();
    connection.stop();
    stubber.stop();
  }

}