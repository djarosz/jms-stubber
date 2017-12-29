package com.github.djarosz.jmsstubber;

public interface JmsStubber {

  /**
   * Header set by stubber to mark message as processed by stubber handlers. Messages handled by JMS stubber
   * are reinserted into original queue with this header set to "true". This avoids infinite handler executions.
   */
  String STUBBER_PROCESSED_HEADER = "JMS_stubber_processed";

  void start();

  void stop();
}
