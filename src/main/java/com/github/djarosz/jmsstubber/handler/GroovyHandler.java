package com.github.djarosz.jmsstubber.handler;

import com.github.djarosz.jmsstubber.HandlerSession;
import com.github.djarosz.jmsstubber.MessageHandler;
import groovy.json.JsonSlurper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.jms.Message;
import javax.jms.TextMessage;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQDestination;


/**
 * For every message received dynamicly compiled groovy script is evaluated.
 * This allows to dynamically alter jms-stubber behaviour with out need for restart and reconfiguration.
 *
 * <p>Handler requires script location. If specified location is a File then this script will be used.
 *
 * <p>If specified location is a Directory then following rules apply:
 * <li>{queueName}.groovy will be called if it exists
 * <li> </li> default.groovy - script will be called if it exists
 *
 * <p>These variables are available during script execution:
 * <li> msg - received JMS message
 * <li> session - HandlerSession
 * <li> log - slf4j logger
 */
@AllArgsConstructor
@Slf4j
public class GroovyHandler implements MessageHandler {

  private static final String DEFAULT_GROOVY_SCRIPT = "default.groovy";

  @NonNull
  private File scriptFileOrDirectory;

  @Override
  public void handle(HandlerSession session, Message message) throws Throwable {
    Optional<File> scriptFile = getScriptFile(message);
    if (!scriptFile.isPresent()) {
      log.warn("[{}]: No groovy script found.");
    }

    Binding binding = new Binding();
    GroovyShell shell = new GroovyShell(binding);

    binding.setVariable("session", session);
    binding.setVariable("log", log);
    binding.setVariable("msg", enhanceMessage(message));

    Script script = shell.parse(scriptFile.get());
    script.setBinding(binding);
    Object result = script.run();

    log.info("[{}]: Script result: {}", message.getJMSDestination(), Objects.toString(result));
  }

  private Optional<File> getScriptFile(Message message) throws Exception {
    ActiveMQDestination destination = (ActiveMQDestination) message.getJMSDestination();
    return Stream.of(
        scriptFileOrDirectory,
        new File(scriptFileOrDirectory, destination.getPhysicalName() + ".groovy"),
        new File(scriptFileOrDirectory, DEFAULT_GROOVY_SCRIPT))
        .filter(File::exists)
        .filter(File::isFile)
        .findFirst();
  }

  private Message enhanceMessage(Message message) {
    if (message instanceof TextMessage) {
      return new ExtendedTextMessage((TextMessage) message);
    }
    return message;
  }

  @AllArgsConstructor
  public class ExtendedTextMessage implements TextMessage {

    @Delegate
    private TextMessage message;

    @SneakyThrows
    public GPathResult getXml() {
      return new XmlSlurper().parseText(message.getText());
    }

    @SneakyThrows
    public Object getJson() {
      return new JsonSlurper().parseText(message.getText());
    }
  }

}
