# JMS Stubber

*JMS Stubber* allow you to stub JMS messaging. If your aplication communicates via JMS with
external system. 

Is assumes each queue/topic is assigned list of *MessageHandlers*. Each handler executes
in the order in which the were defined. You can define *common handlers* 
executed for every queue and per *destination handlers* (queue/topic). *common handlers* are
executed first. Original JMS message is left in the queue for consumption by external systems.
This is done by resending handled message to the original destination but addig special JMS Header
so handlers wont execute infinitely. 

*JMS Stubber* is based on [ActiveMQ](http://activemq.apache.org) so it supports any 
(transport protocol)[http://activemq.apache.org/protocols.html]
which is supported by [ActiveMQ](http://activemq.apache.org). 
To enable specific transport protocol support some more dependecies migh bee needed 
(only tested using tcp wich openwire and vm transports). You specify transport protocol
by *ConnectorUri* property of the JmsStubberBuilder or property in configuration file when
JMSStubber is run in server mode.

There are two ways to configure and execute *JMS Stubber*:

- build your configuration using *com.github.djarosz.jmsstubber.JmsStubberBuilder*
  and then call *JmsStubber.start()/JmsStubber.stop()*
- prepare configuration file and run server using 
  *com.github.djarosz.jmsstubber.JmStubberRunner* 

There are also two ways to use ActiveMQ
- use embbedded broker (*JmsStubberBuilder.embeddedBroker()*)
- (not tested) use remote ActiveMQ broker (*JmsStubberBuilder.amqConnectionFactory()* 
  or setting *connection.factory.uri* in server mode)

### Available message handlers 

- LoggingHandler - Logs received message ([see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/LoggingHandler.java))
- ForwardingHandler - Forwards message to specified destination ([see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/ForwardingHandler.java))
- MessageCollectingHandler - Used for testing stores every received message in *LinkedList* ([see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/MessageCollectingHandler .java))
- GroovyHandler - Executes groovy script for every (evaluated on every message) ([see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/GroovyHandler .java))

### GroovyHandler

This handler needs more explanation. Please see ([see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/GroovyHandler .java))

For every message received dynamicly compiled groovy script is evaluated.
This allows to dynamically alter jms-stubber behaviour with out need for restart and reconfiguration.

Handler requires script location. If specified location is a File then this script will be used.

If specified location is a Directory then following rules apply:
- {queueName}.groovy will be called if it exists
- default.groovy - script will be called if it exists

These variables are available during script execution:
- *msg* - received JMS message
- *session* - [HandlerSession](src/main/java/com/github/djarosz/jmsstubber/HandlerSession.java)
- *log* - slf4j logger

*Example script*
```groovy
import groovy.xml.MarkupBuilder

def xml = msg.xml
def child0 = xml.child[0]
def child1 = xml.child[1]

def writer = new StringWriter()
def xmlResponse = new MarkupBuilder(writer)

xmlResponse.'PARENT' {
    int i = 0;
    for (child in xml.child) {
        'CHILD'(id: i++, child)
    }
}

session.send("out", writer.toString())

```

## Using JmsStubberBuilder

You can build your *JMS Stubber configuration using builder and then call *start()/stop()*
on JmsStubber instance.

```java
GroovyHandler groovyHandler = new GroovyHandler(new File("target/test-classes"));
MessageCollectingHandler<TextMessage> messageStore = new MessageCollectingHandler<>();
JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
    .withQueues()
      .withCommonMessageHandler(LoggingHandler.INSTANCE)
      .withCommonMessageHandler(messageStore)
      .withQueue("test.queue.out")
      .withQueue("test.queue.in", groovyHandler)
    .build();

stubber.start();

// ....

stubber.stop();
```

## Using JmsStubberRunner (server mode)

You can run JmsStubber in serwer mode. First prepare simple properties file with 
queue/topic configuration. And then execute following command;

```bash
java -jar jms-stubber-<version>-runner.jar stubber.properties
```

Not that when using *com.github.djarosz.jmsstubber.handler.GroovyHandler* you can edit
groovy scripts without restarting the main app.

### Example configuration file

*With embedded broker*
```properties
connector.uri.1=tcp://localhost:5678
queue.handler.1=com.github.djarosz.jmsstubber.handler.LoggingHandler
queue.handler.2=com.github.djarosz.jmsstubber.handler.MessageCollectingHandler
queue.out.name=test.queue.out
queue.in.name=test.queue.in
queue.in.handler.1=com.github.djarosz.jmsstubber.handler.GroovyHandler,target/test-classes
```

*With connection facotry posibly to remote ActiveMQ instance*
```properties
connection.factory.uril=tcp://localhost:1234
queue.handler.1=com.github.djarosz.jmsstubber.handler.LoggingHandler
queue.handler.2=com.github.djarosz.jmsstubber.handler.MessageCollectingHandler
queue.out.name=test.queue.out
queue.in.name=test.queue.in
queue.in.handler.1=com.github.djarosz.jmsstubber.handler.GroovyHandler,target/test-classes
```

## TODO
- support for topics
- error handling

## License

jms-stubber is licensed under the [MIT](./LICENSE).
