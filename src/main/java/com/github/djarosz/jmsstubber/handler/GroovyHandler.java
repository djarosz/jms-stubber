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
import javax.jms.Message;
import javax.jms.TextMessage;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class GroovyHandler implements MessageHandler {

  @NonNull
  private File scriptFile;

  @Override
  public void handle(HandlerSession session, Message message) throws Throwable {
    Binding binding = new Binding();
    GroovyShell shell = new GroovyShell(binding);
    Script script = shell.parse(scriptFile);

    binding.setVariable("session", session);
    binding.setVariable("log", log);
    binding.setVariable("msg", enhanceMessage(message));
    binding.setVariable("replyTo", null);

    script.setBinding(binding);
    Object result = script.run();

    log.info("[{}]: Script result: {}", message.getJMSDestination(), Objects.toString(result));

    String replyTo = (String) binding.getVariable("replyTo");
    if (replyTo != null) {
      session.send(replyTo, result.toString());
    }

    // TODO send response based on result type
    // - Message - > Message
    // - MarkupBuilder, StreamingMarkupBuilder, xmlstubpler, xmlparser, gruoov json, xml, json, String -> TextMessage
    // - Object -> ObjectMessage
  }

  private Message enhanceMessage(Message message) {
    if (message instanceof TextMessage) {
      return new ExtendedTextMessage((TextMessage) message);
    }
    return message;
  }

  @AllArgsConstructor
  public class ExtendedTextMessage implements TextMessage {

    @lombok.Delegate
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
