package com.github.djarosz.jmsstubber.handler;

import com.github.djarosz.jmsstubber.HandlerSession;
import com.github.djarosz.jmsstubber.MessageHandler;
import groovy.json.JsonSlurper;
import groovy.lang.Binding;
import groovy.lang.Script;
import groovy.util.GroovyScriptEngine;
import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import java.io.File;
import java.net.URL;
import java.util.Optional;
import java.util.stream.Stream;
import javax.jms.Message;
import javax.jms.TextMessage;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQDestination;


/**
 * For every message received dynamically compiled groovy script is evaluated. This allows to
 * dynamically alter jms-stubber behaviour with out need for restart and reconfiguration.
 *
 * <p>Handler requires script location. If specified location is a File then this script will be
 * used.
 *
 * <p>If specified location is a Directory then following rules apply: <li>{queueName}.groovy will
 * be called if it exists <li>default.groovy - script will be called if it exists
 *
 * <p>These variables are available during script execution: <li> msg - received JMS message <li>
 * session - HandlerSession <li> log - slf4j logger
 */
@Slf4j
public class GroovyHandler implements MessageHandler {

  private static final String DEFAULT_GROOVY_SCRIPT = "default.groovy";

  private File scriptFileOrDirectory;
  private GroovyScriptEngine scriptEngine;

  @SneakyThrows
  public GroovyHandler(File scriptFileOrDirectory) {
    this.scriptFileOrDirectory = scriptFileOrDirectory;
    URL scriptEngineBaseUrl = scriptFileOrDirectory.isDirectory()
        ? scriptFileOrDirectory.toURL()
        : scriptFileOrDirectory.getParentFile().toURL();
    this.scriptEngine = new GroovyScriptEngine(new URL[]{scriptEngineBaseUrl});
  }

  @Override
  public void handle(HandlerSession session, Message message) throws Throwable {
    Optional<String> scriptFile = getScriptFile(message);
    if (!scriptFile.isPresent()) {
      log.warn("[{}]: No groovy script found.");
    }

    log.info("[{}]: Executing script: {}", message.getJMSDestination(), scriptFile.get());

    Binding binding = new Binding();
    binding.setVariable("session", session);
    binding.setVariable("log", log);
    binding.setVariable("msg", enhanceMessage(message));

    Script script = scriptEngine.createScript(scriptFile.get(), binding);

    script.setBinding(binding);
    script.run();

    log.info("[{}]: Script executed", message.getJMSDestination());
  }

  private Optional<String> getScriptFile(Message message) throws Exception {
    ActiveMQDestination destination = (ActiveMQDestination) message.getJMSDestination();
    return Stream.of(
        scriptFileOrDirectory,
        new File(scriptFileOrDirectory, destination.getPhysicalName() + ".groovy"),
        new File(scriptFileOrDirectory, DEFAULT_GROOVY_SCRIPT))
        .filter(File::exists)
        .filter(File::isFile)
        .map(File::getName)
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
