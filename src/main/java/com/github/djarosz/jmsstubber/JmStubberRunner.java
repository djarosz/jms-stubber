package com.github.djarosz.jmsstubber;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

/**
 * Run JmsStubber as a daemon.
 */
public class JmStubberRunner {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("No configuration file passed as first argument.");
      System.exit(1);
    }

    System.out.println("Starting...");

    Properties configuration = new Properties();
    configuration.load(new FileReader(new File(args[0])));
    JmsStubber jmsStubber = JmStubberPropertyConfigurer.build(configuration);
    jmsStubber.start();

    System.out.println("Started.");

    waitOnShutdownAndExecute(() -> {
      System.out.println("Stopping...");
      jmsStubber.stop();
      System.out.println("Stopped.");
    });
  }

  private static void waitOnShutdownAndExecute(Runnable onShutdownHandler)
      throws InterruptedException {
    Thread stubberThread = new JmsStubberRunnerThread(onShutdownHandler);
    stubberThread.start();
    stubberThread.join();
  }

  private static class JmsStubberRunnerThread extends Thread {

    private Object lock = new Object();
    private Runnable onShutdownHandler;

    JmsStubberRunnerThread(Runnable onShutdownHandler) {
      super("jms-stubber-runner");
      this.onShutdownHandler = onShutdownHandler;
    }

    @Override
    public void run() {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        onShutdownHandler.run();
        synchronized (lock) {
          lock.notify();
        }
      }));

      synchronized (lock) {
        try {
          lock.wait();
        } catch (InterruptedException e) {
          // ignore
        }
      }
    }
  }

}
