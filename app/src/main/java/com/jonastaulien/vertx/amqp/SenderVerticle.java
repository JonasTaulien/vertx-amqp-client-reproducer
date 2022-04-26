package com.jonastaulien.vertx.amqp;

import io.reactivex.Completable;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.reactivex.amqp.AmqpClient;
import io.vertx.reactivex.amqp.AmqpMessageBuilder;
import io.vertx.reactivex.amqp.AmqpSender;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SenderVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(InitialRetryVerticle.class);
    private final AmqpClient amqpClient;
    private final Vertx vertx;



    public SenderVerticle(AmqpClient amqpClient, Vertx vertx) {
        this.amqpClient = amqpClient;
        this.vertx = vertx;
    }



    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        AmqpClientOptions amqpClientOptions
                = new AmqpClientOptions().setHost("localhost")
                                         .setPort(5672)
                                         .setUsername("artemis")
                                         .setPassword("artemis")
                                         .setConnectTimeout(5000)
                                         .setHeartbeat(1000);

        AmqpClient amqpClient = AmqpClient.create(vertx, amqpClientOptions);

        vertx.rxDeployVerticle(() -> new SenderVerticle(amqpClient, vertx), new DeploymentOptions())
             .subscribe(
                     did -> log.info("Successfully created sender"),
                     err -> log.error("Failed to create sender", err)
             );
    }



    @Override
    public Completable rxStart() {
        return this.amqpClient.rxCreateSender("example_address")
                              .flatMap(
                                      sender -> this.vertx.createHttpServer()
                                                          .requestHandler(req -> this.sendMessage(sender, req))
                                                          .rxListen(8080)
                              )
                              .ignoreElement();
    }



    public void sendMessage(AmqpSender sender, HttpServerRequest request) {
        log.info("Trying to send message to message broker");

        sender.rxSendWithAck(
                      AmqpMessageBuilder.create()
                                        .withBody("a message body")
                                        .build()
              )
              .subscribe(
                      () -> {
                          log.info("Successfully send message");
                          request.response().end("Successfully send message");
                      },
                      err -> {
                          log.error("Failed to send message", err);
                          request.response().setStatusCode(500).end("Failed to send message");
                      }
              );
    }
}
