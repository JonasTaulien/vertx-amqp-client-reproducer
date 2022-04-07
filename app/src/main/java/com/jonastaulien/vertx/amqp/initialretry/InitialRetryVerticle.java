package com.jonastaulien.vertx.amqp.initialretry;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.amqp.AmqpClient;
import io.vertx.reactivex.amqp.AmqpMessageBuilder;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class InitialRetryVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(InitialRetryVerticle.class);
    private final AmqpClient amqpClient;
    private final Vertx vertx;



    public InitialRetryVerticle(AmqpClient amqpClient, Vertx vertx) {
        this.amqpClient = amqpClient;
        this.vertx = vertx;
    }



    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        AmqpClientOptions amqpClientOptions = new AmqpClientOptions()
                .setHost("localhost")
                .setPort(5672)
                .setUsername("artemis")
                .setPassword("artemis")
                .setConnectTimeout(5000)
                // There is no problem with this - It works as expected when ActiveMQ Artemis is NOT started as a
                //   docker container
                .setReconnectAttempts(10)
                .setReconnectInterval(1000);

        AmqpClient amqpClient = AmqpClient.create(vertx, amqpClientOptions);

        vertx.rxDeployVerticle(() -> new InitialRetryVerticle(amqpClient, vertx), new DeploymentOptions())
             .subscribe(
                     did -> log.info("Successfully send message"),
                     err -> log.error("Failed to create sender", err)
             );
    }



    @Override
    public Completable rxStart() {
        return this.amqpClient.rxCreateSender("example_address")
                              // We observed, that when starting ActiveMQ Artemis via docker, is throws
                              //   `io.vertx.core.VertxException: Disconnected` exception on the last retry.
                              //   Because of that, we have to introduce this additional retry.
                              .retryWhen(errors -> errors.flatMap(err -> {
                                  log.warn("Build-in initial reconnect failed. Trying again in 1000ms", err);

                                  return Flowable.timer(
                                          1000, TimeUnit.MILLISECONDS,
                                          RxHelper.scheduler(this.vertx.getDelegate())
                                  );
                              }))
                              .flatMapCompletable(sender -> sender.rxSendWithAck(
                                      AmqpMessageBuilder.create()
                                                        .withBody("a message body")
                                                        .build()
                              ));
    }
}
