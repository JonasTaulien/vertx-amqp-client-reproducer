package com.jonastaulien.vertx.amqp;

import io.reactivex.Completable;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.reactivex.amqp.AmqpClient;
import io.vertx.reactivex.amqp.AmqpMessage;
import io.vertx.reactivex.amqp.AmqpReceiver;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReceiverVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(InitialRetryVerticle.class);
    private final AmqpClient amqpClient;



    public ReceiverVerticle(AmqpClient amqpClient) {
        this.amqpClient = amqpClient;
    }



    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        AmqpClientOptions amqpClientOptions
                = new AmqpClientOptions().setHost("localhost")
                                         .setPort(5672)
                                         .setUsername("artemis")
                                         .setPassword("artemis")
                                         .setConnectTimeout(5000);

        AmqpClient amqpClient = AmqpClient.create(vertx, amqpClientOptions);

        vertx.rxDeployVerticle(() -> new ReceiverVerticle(amqpClient), new DeploymentOptions())
             .subscribe(
                     did -> log.info("Successfully created receiver"),
                     err -> log.error("Failed to create receiver", err)
             );
    }



    @Override
    public Completable rxStart() {
        return this.amqpClient.rxCreateReceiver("example_address")
                              .doOnSuccess(this::handleMessages)
                              .ignoreElement();
    }



    private void handleMessages(AmqpReceiver receiver) {
        receiver.toObservable()
                .subscribe(
                        msg -> log.info("Successfully received a message: {}", msg.bodyAsString()),
                        err -> log.error("Receiver failed", err),
                        () -> log.warn("Receiver completed unexpectedly")
                );
    }
}
