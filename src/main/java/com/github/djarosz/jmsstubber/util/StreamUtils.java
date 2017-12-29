package com.github.djarosz.jmsstubber.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Stream utils.
 */
public interface StreamUtils {

  static <T> Stream<T> streamOf(Collection<T> items) {
    return (items == null ? Collections.<T>emptyList() : items).stream();
  }

  static <T> Stream<T> mergeAsStream(Collection<T>... items) {
    if (items == null) {
      return Stream.empty();
    }
    return Stream.of(items).filter(Objects::nonNull).map(Collection::stream).flatMap(Function.identity());
  }

  static <T> Stream<T> mergeAsStream(Collection<Collection<T>> items) {
    if (items == null) {
      return Stream.empty();
    }
    return items.stream().filter(Objects::nonNull).map(Collection::stream).flatMap(Function.identity());
  }

  static <T> Stream<T> merge(Collection<Stream<T>> streams) {
    if (streams == null || streams.isEmpty()) {
      return Stream.empty();
    }
    return streams.stream().flatMap(Function.identity());
  }

  static <T> Stream<T> merge(Stream<T>... streams) {
    if (streams == null) {
      return Stream.empty();
    }
    return Stream.of(streams).flatMap(Function.identity());
  }
}
