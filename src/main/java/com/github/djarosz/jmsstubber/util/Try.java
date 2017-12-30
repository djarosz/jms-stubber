package com.github.djarosz.jmsstubber.util;

public class Try {

  public interface ThrowingSupplier<T> {
    T get() throws Throwable;
  }

  public static <T> T orNull(ThrowingSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (Throwable e) {
      return null;
    }
  }

}
