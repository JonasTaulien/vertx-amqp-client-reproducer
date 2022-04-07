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

## Problem description
We are trying to build a resilient connection to our message broker. Resilient means the following to us:

After the successful creation of a sender or receiver, if the sender loses the connection to the message broker, the
application tries to reconnect until the message broker is present again and the connection could be re-established

For this to work, we identified the following requirements for the AMQP client. Each requirement also contains a
description what problems we currently see with this specific requirement.

