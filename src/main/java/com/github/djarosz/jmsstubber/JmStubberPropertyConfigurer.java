package com.github.djarosz.jmsstubber;

import com.github.djarosz.jmsstubber.util.Try;
import com.github.djarosz.jmsstubber.util.Try.ThrowingSupplier;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;


/**
 * Build JmsStubber from properties
 *
 * <p>Optional parameters
 * <li>connection.factory.url=
 * <li>connector.uri.1=
 * <li>connector.uri.2=
 * <li>queue.handler.1=class_name,constructor_arg1,constructor_arg2
 * <li>queue.handler.2=class_name,constructor_arg1,constructor_arg2
 *
 * <p>At least one queue definition is required
 * <li>queue.logical_queue_name_a.name=A.QUEUE
 * <li>queue.logical_queue_name_a.handler.1=class_name,constructor_arg1,constructor_arg2
 * <li>queue.logical_queue_name_b.name=B.QUEUE
 * <li>queue.logical_queue_name_b.handler.1=class_name,constructor_arg1,constructor_arg2
 */
@Slf4j
public class JmStubberPropertyConfigurer {

  private JmsStubberBuilder builder;
  private Properties configProps;

  public JmStubberPropertyConfigurer(Properties configProps) {
    this.configProps = configProps;
    initializeBuilder();
    addCommonQueueHandlers();
    addQueueDefinitions();
  }

  public static JmsStubber build(Properties properties) {
    return new JmStubberPropertyConfigurer(properties).getJmsStubber();
  }

  public JmsStubber getJmsStubber() {
    return builder.build();
  }

  private void initializeBuilder() {
    String connectionFactoryUri = configProps.getProperty("connection.factory.uri");
    if (connectionFactoryUri == null) {
      JmsStubberBuilder.EmbeddedBrokerBuilder brokerBuilder = JmsStubberBuilder.embeddedBroker();
      enumeratedProps("connector.uri").forEach(brokerBuilder::withConnectorUri);
      builder = brokerBuilder.withQueues();
    } else {
      builder = JmsStubberBuilder
          .amqConnectionFactory(new ActiveMQConnectionFactory(connectionFactoryUri));
    }
  }

  private void addCommonQueueHandlers() {
    enumeratedProps("queue.handler")
        .map(this::createHandler)
        .forEach(builder::withCommonMessageHandler);
  }

  private void addQueueDefinitions() {
    //language=RegExp
    Map<String, String> queues = labelToValue("^queue\\.(.+)\\.name$");
    for (Map.Entry<String, String> queueEntry : queues.entrySet()) {
      String queueName = queueEntry.getValue();
      MessageHandler[] handlers = enumeratedProps("queue." + queueEntry.getKey() + ".handler")
          .map(this::createHandler)
          .toArray(MessageHandler[]::new);

      builder.withQueue(queueName, handlers);
    }
  }

  // TODO add more types and error reporting
  @SneakyThrows
  private MessageHandler createHandler(String description) {
    String[] args = description.split(",");
    Class handlerClass = Class.forName(args[0]);
    Constructor constructor = Stream.of(handlerClass.getConstructors())
        .filter(c -> c.getParameterCount() == args.length - 1)
        .findFirst()
        .get();

    String[] stringArgValues = Arrays.copyOfRange(args, 1, args.length);
    Object[] constructorArgs = buildConstructorArgs(constructor, stringArgValues);

    return (MessageHandler) constructor.newInstance(constructorArgs);
  }

  @SneakyThrows
  private Object[] buildConstructorArgs(Constructor constructor, String[] stringArgValues) {
    Object[] constructorArgs = new Object[constructor.getParameterCount()];
    for (int i = 0; i < constructor.getParameterCount(); i++) {
      String stringParamValue = stringArgValues[i];

      if (stringParamValue.trim().isEmpty()) {
        constructorArgs[i] = null;
        continue;
      }

      Class paramType = constructor.getParameterTypes()[i];
      constructorArgs[i] = Stream
          .<ThrowingSupplier>of(
              () -> String.class.isAssignableFrom(paramType) ? stringParamValue : null,
              () -> paramType.getConstructor(String.class).newInstance(stringParamValue),
              () -> paramType.getMethod("valueOf", String.class).invoke(null, stringParamValue))
          .map(Try::orNull)
          .filter(Objects::nonNull)
          .findFirst()
          .orElseThrow(() -> new RuntimeException(
              "Could not convert " + stringParamValue + " to object of " + paramType.getName()));
    }
    return constructorArgs;
  }

  private Map<String, String> labelToValue(String regexProp) {
    Map<String, String> map = new HashMap<>();
    Pattern pattern = Pattern.compile(regexProp);
    for (Enumeration<?> propNameEn = configProps.propertyNames(); propNameEn.hasMoreElements(); ) {
      String propName = (String) propNameEn.nextElement();
      Matcher matcher = pattern.matcher(propName);
      if (matcher.matches()) {
        map.put(matcher.group(1), configProps.getProperty(propName));
      }
    }

    return map;
  }

  private Stream<String> enumeratedProps(String propPrefix) {
    return IntStream.range(1, 100)
        .mapToObj(i -> configProps.getProperty(propPrefix + "." + i))
        .filter(Objects::nonNull);
  }
}
