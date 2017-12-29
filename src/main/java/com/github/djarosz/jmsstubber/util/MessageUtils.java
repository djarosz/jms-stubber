package com.github.djarosz.jmsstubber.util;

import java.util.Enumeration;
import java.util.function.BiConsumer;
import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class MessageUtils {

  /**
   * Creates copy of a JMS message.
   */
  @SneakyThrows
  public static <T extends Message> T createCopy(Session session, T message) {
    Message copy = null;
    if (message instanceof ObjectMessage) {
      copy = copy(session::createObjectMessage, MessageUtils::copyObjectMessage, (ObjectMessage) message);
    } else if (message instanceof TextMessage) {
      copy = copy(session::createTextMessage, MessageUtils::copyTextMessage, (TextMessage) message);
    } else if (message instanceof MapMessage) {
      copy = copy(session::createMapMessage, MessageUtils::copyMapMessage, (MapMessage) message);
    } else if (message instanceof BytesMessage) {
      copy = copy(session::createBytesMessage, MessageUtils::copyBytesMessage, (BytesMessage) message);
    } else if (message instanceof StreamMessage) {
      copy = copy(session::createStreamMessage, MessageUtils::copyStreamMessage, (StreamMessage) message);
    }

    return (T) copy;
  }

  @FunctionalInterface
  public interface MessageCreator<T extends Message> {
    T get() throws Throwable;
  }

  @SneakyThrows
  private static <T extends Message> T copy(MessageCreator<T> creator, BiConsumer<T, T> copier, T message) {
    T copy = creator.get();
    copyMessageAttributes(copy, message);
    copier.accept(copy, message);
    return copy;
  }

  @SneakyThrows
  private static void copyStreamMessage(StreamMessage dst, StreamMessage src) {
    byte[] buffer = new byte[1024];
    int count = 0;
    do {
      count = src.readBytes(buffer);
      dst.writeBytes(buffer, 0, count);
    } while (count == buffer.length);
  }

  @SneakyThrows
  private static void copyBytesMessage(BytesMessage dst, BytesMessage src) {
    byte[] buffer = new byte[1024];
    int count = 0;
    do {
      count = src.readBytes(buffer);
      dst.writeBytes(buffer, 0, count);
    } while (count == buffer.length);
  }

  @SneakyThrows
  private static void copyMapMessage(MapMessage dst, MapMessage src) {
    for (Enumeration<String> mapNamesEn = src.getMapNames(); mapNamesEn.hasMoreElements(); ) {
      String name = mapNamesEn.nextElement();
      Object value = src.getObject(name);
      dst.setObject(name, value);
    }
  }

  @SneakyThrows
  private static void copyTextMessage(TextMessage dst, TextMessage src) {
    dst.setText(src.getText());
  }

  @SneakyThrows
  private static void copyObjectMessage(ObjectMessage dst, ObjectMessage src) {
    dst.setObject(src.getObject());
  }

  @SneakyThrows
  private static <T extends Message> void copyMessageAttributes(T dst, T src) {
    for (Enumeration<String> propNameEnum = src.getPropertyNames(); propNameEnum.hasMoreElements(); ) {
      String propName = propNameEnum.nextElement();
      Object propValue = src.getObjectProperty(propName);
      dst.setObjectProperty(propName, propValue);
    }
  }

}
