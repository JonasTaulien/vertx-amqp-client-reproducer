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
[InitialRetryVerticle.java](./app/src/main/java/com/jonastaulien/vertx/amqp/InitialRetryVerticle.java).

Check :)

#### Reproduce (the success)
1. Make sure you followed the steps from the 'Prerequisites to run this reproducer'-section
2. Run [InitialRetryVerticle.java](./app/src/main/java/com/jonastaulien/vertx/amqp/InitialRetryVerticle.java) inside
your IDE
3. After a few seconds start Artemis by executing (bash)

    ```sh
    ./start-artemis.sh
    ```
   or (windows)
    ```powershell
    .\start-artemis.bat
    ```
4. You should be able to see the sent message in the Artemis Console at
[http://localhost:8161/console](http://localhost:8161/console)
   1. Login with `artemis` and `artemis`
   2. In the Tree select `0.0.0.0` > `addresses` -> `example_address` -> `queues` -> `anycast` -> `example_address`
   3. Click on the `More v`-Tab
   4. Select `Browse Queue`

### Reconnect on connection loss - Sender
After the successful creation of a sender, if the sender loses the connection to the message broker, the application
tries to reconnect until the message broker is present again and the connection could be re-established.

For this to work, the `AmqpSender#rxSendWithAck` method must complete with an error if the connection got lost.
**But currently we do not get any response back and `AmqpSender#rxSendWithAck` executes indefinitely**.

#### Reproduce (the problem)
1. Make sure you followed the steps from the 'Prerequisites to run this reproducer'-section
2. Start Artemis by executing (bash)

    ```sh
    ./start-artemis.sh
    ```
   or (windows)
    ```powershell
    .\start-artemis.bat
    ```
3. Run [SenderVerticle.java](./app/src/main/java/com/jonastaulien/vertx/amqp/SenderVerticle.java) inside your IDE
4. Open [http://localhost:8080](http://localhost:8080) in your browser. This will trigger the sending of a message
   to the message broker. It should succeed with `Successfully send message`.
5. Stop the execution of Artemis by hitting Ctrl-C or by closing the shell.
   You will see the following log in the execution of the Verticle

    ```log
    2022-04-26 12:26:17.821|TRACE|vert.x-eventloop-thread-0|New Proton Event: CONNECTION_REMOTE_CLOSE
    2022-04-26 12:26:17.823|TRACE|vert.x-eventloop-thread-0|New Proton Event: TRANSPORT_TAIL_CLOSED
    2022-04-26 12:26:17.823|TRACE|vert.x-eventloop-thread-0|New Proton Event: CONNECTION_LOCAL_CLOSE
    2022-04-26 12:26:17.823|TRACE|vert.x-eventloop-thread-0|New Proton Event: TRANSPORT_HEAD_CLOSED
    2022-04-26 12:26:17.823|TRACE|vert.x-eventloop-thread-0|New Proton Event: TRANSPORT_CLOSED
    2022-04-26 12:26:17.833|TRACE|vert.x-eventloop-thread-0|IdleTimeoutCheck cancelled
    ```

6. Refresh [http://localhost:8080](http://localhost:8080) or open the URL again.
   It will log `Trying to send message to message broker` but it never completes and runs indefinitely

#### Possible solutions
##### Sender throws error after connection loss
If `AmqpSender#rxSendWithAck` would respond with an error, we could try to recreate the sender. Then we would be
able to recover from short-term connection losses.

##### Manually set a timout
What we currently **can** do is to set a manual timeout for the sending of a message and then to recreate the sender
after the timout. But considering that the Sender internally already detects that the connection is lost, we would be
able to fail faster if it would just fail.

##### Detect a disconnected connection using `AmqpSender.connection().isDisconnected()`
We also found that we can use `AmqpSender.connection().isDisconnected()` to detect a disconnected connection. This
is a workaround we are currently using. But what, if the connection fails between the two calls of
`AmqpSender.connection().isDisconnected()` and `AmqpSender#rxSendWithAck`, then we still have the problem that
`AmqpSender#rxSendWithAck` never completes.

See [ReconnectSenderVerticle.java](./app/src/main/java/com/jonastaulien/vertx/amqp/ReconnectSenderVerticle.java) for an
implementation of this.

###### Observed bug with `AmqpSender.rxClose()`
We call close on the previous sender in case the connection was lost. See `getOrRecreateSender` in
[ReconnectSenderVerticle.java](./app/src/main/java/com/jonastaulien/vertx/amqp/ReconnectSenderVerticle.java)

But `AmqpSender.rxClose()` does not succeed. We debugged the code and the reason is that, even if
`AmqpSenderImpl.close()` sets a `closeHandler` on the underlying `ProtonSender`, the `ProtonSender` never calls that
close handler. That's why the completable never completes.

### Reconnect on connection loss - Receiver
After the successful creation of a receiver, if the receiver loses the connection to the message broker, the application
tries to reconnect until the message broker is present again and the connection could be re-established.

For this to work, we identified the following requirements for the AMQP client:
