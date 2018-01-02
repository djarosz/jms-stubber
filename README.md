# JMS Stubber

*JMS Stubber* allow you to stub JMS messaging. Eg. If your application communicates via JMS with
external system in request-response manner you can use *JMS Stubber to build and send response
to response queue upon receiving message in request queue

Each destination (queue or topic) is assigned list of *MessageHandlers*. Handlers execute
in the order in which they were defined. You can define *common handlers* which 
execute for every messag in every queue. You can also define destination specific *destination handlers* (queue/topic).
*common handlers* are executed first. Original JMS message is left in the queue for
consumption by external systems. This is done by resending handled message to the 
original destination but addig special JMS header so handlers will not be exeuted second time causing
a infinite loop of execution. 

*JMS Stubber* is based on [ActiveMQ](http://activemq.apache.org) so it supports all *ActiveMQ*
[transport protocol](http://activemq.apache.org/protocols.html).
To enable specific transport protocol support some more dependecies migh bee needed on your classpath.
(only tested using tcp/openwire and vm transports). You specify transport protocol
by *connectorUri* property of the *JmsStubberBuilder* or property in configuration file when
*JMS Stubber* is run in server mode.

There are two ways to configure and execute *JMS Stubber*:

- build your configuration using *com.github.djarosz.jmsstubber.JmsStubberBuilder*
  and then call *JmsStubber.start()/JmsStubber.stop()*
- prepare configuration file and run server using 
  *com.github.djarosz.jmsstubber.JmStubberRunner* 

There are also two ways to use ActiveMQ
- use embbedded broker (*JmsStubberBuilder.embeddedBroker()* or **not** setting 
  *connection.factory.uri* in server mode)
- (not tested) use remote ActiveMQ broker (*JmsStubberBuilder.amqConnectionFactory()* 
  or setting *connection.factory.uri* in server mode)

### Available message handlers 

- LoggingHandler - Logs received message [see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/LoggingHandler.java)
- ForwardingQueueHandler - Forwards message to specified queue [see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/ForwardingQueueHandler.java)
- ForwardingTopicHandler - Forwards message to specified topic [see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/ForwardingTopicHandler.java)
- MessageCollectingHandler - Used for testing stores every received message in *LinkedList* [see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/MessageCollectingHandler.java)
- GroovyHandler - Executes groovy script for every (evaluated on every message) [see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/GroovyHandler.java)

### GroovyHandler

This handler needs more explanation. Please [see javadoc](src/main/java/com/github/djarosz/jmsstubber/handler/GroovyHandler.java)

For every message received dynamically compiled groovy script is evaluated.
This allows to dynamically alter jms-stubber behaviour with out need for restart and reconfiguration.

Handler requires script location. If specified location is a File then this script will be used.

If specified location is a Directory then following rules apply:
- {queueName}.groovy will be called if it exists
- default.groovy - script will be called if it exists

These variables are available during script execution:
- *msg* - received JMS message
- *session* - [HandlerSession](src/main/java/com/github/djarosz/jmsstubber/HandlerSession.java)
- *log* - slf4j logger

*Example script - which send response to *out* queue upon receiving message*
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

### Register common handlers on all queues

By specifying *JmsStubberBuilder.registerCommonHandlersOnAllQueueus* or setting
*register.common.handlers.on.all.queues* property to *true*. You tell JmsStubber to listen 
on all queues even if they were ones not registered using *JmsStubberBuilder.withQueues()*.
Such queues will only be assigned *common handlers*.

```java
JmsStubber stubber = JmsStubberBuilder.embeddedBroker()
    .registerCommonHandlersOnAllQueues()
    .withQueues()
      .withCommonMessageHandler(LoggingHandler.INSTANCE)
    .build();
```

This in combination with groovy handler lets you configure generic stubber for which
behavior can be modified during runtime.

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
In above examples *queue.<queue_key>.handler.X* has a special format:

```
<class.name>,<constuctor_param1>,<constructor_param2>,...
```

**NOTE** For now conversion of constructor parameters from string to object values
is not very sophisticated.

## TODO
- message senders - idea is to be able to insert new messags to queue on demand.
  Currently you can only insert messages upon receiving another message.
  Some ideas:
  - command line to insert text message to queue. Text can me readed from
    - command parameters
    - file passed as command parameter
    - text piped to as stdin
    - executing groovy script
  - TimerTask periodicly executing groovy script which can insert new message
- support for topics
- error handling
- better docs

## License

jms-stubber is licensed under the [MIT](./LICENSE).
