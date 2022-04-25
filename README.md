# Vert.x AMQP client reproducer
## Context
In our application we are using
* `vert.x:4.2.6`
* `vertx-amqp-client:4.2.6`
* `vertx-rx-java2:4.2.6`

And as a message broker we are using ActiveMQ Artemis in the version `2.21.0`.

## Prerequisites to run this reproducer
1. Download and unpack the latest build of ActiveMQ Artemis from
   [here](https://activemq.apache.org/components/artemis/download/)
2. Make sure artemis (`<download-location>/apache-artemis-2.21.0/bin/artemis`) is available on your PATH
3. You need at least java 11
4. Init Artemis by executing (bash)

    ```sh
    ./init-artemis.sh
    ```
    or (windows)
    ```powershell
    .\init-artemis.bat
    ```

## Problem description
We are trying to build a resilient connection to our message broker. Resilient means the following to us:

### Retry initial connection attempts
On application startup, if the message broker is not yet available, retry to connect until it is available.
We managed to do this by using the `AmqpClientOptions#setReconnectAttempts()` and
`AmqpClientOptions#setReconnectInterval()` methods.
This works as long as ActiveMQ Artemis is not started inside a docker container. We implemented a workaround for
ActiveMq Artemis inside docker. See:
[InitialRetryVerticle.java](./app/src/main/java/com/jonastaulien/vertx/amqp/initialretry/InitialRetryVerticle.java).

Check :)

#### Reproduce (the success)
1. Make sure you followed the steps from the 'Prerequisites to run this reproducer'-section
2. Run InitialRetryVerticle.java inside your IDE
3. After a few seconds start Artemis by executing (bash)

    ```sh
    ./start-artemis.sh
    ```
   or (windows)
    ```powershell
    .\start-artemis.bat
    ```
4. You should be able to the sent message in the Artemis Console at http://localhost:8161/console
   1. Login with `artemis` and `artemis`
   2. In the Tree select `0.0.0.0` > `addresses` -> `example_address` -> `queues` -> `anycast` -> `example_address`
   3. Click on the `More v`-Tab
   4. Select `Browse Queue`

### Reconnect on connection loss - Sender
After the successful creation of a sender, if the sender loses the connection to the message broker, the application
tries to reconnect until the message broker is present again and the connection could be re-established.

For this to work, we identified the following requirements for the AMQP client:

### Reconnect on connection loss - Receiver
After the successful creation of a receiver, if the receiver loses the connection to the message broker, the application
tries to reconnect until the message broker is present again and the connection could be re-established.

For this to work, we identified the following requirements for the AMQP client:
