package com.jonastaulien.vertx.amqp;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.amqp.AmqpClient;
import io.vertx.reactivex.amqp.AmqpMessageBuilder;
import io.vertx.reactivex.amqp.AmqpSender;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ReconnectSenderVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(InitialRetryVerticle.class);
    private final AmqpClient amqpClient;
    private AmqpSender sender = null;



    public ReconnectSenderVerticle(AmqpClient amqpClient) {
        this.amqpClient = amqpClient;
    }



    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        AmqpClientOptions amqpClientOptions
                = new AmqpClientOptions().setHost("localhost")
                                         .setPort(5672)
                                         .setUsername("artemis")
                                         .setPassword("artemis")
                                         .setConnectTimeout(5000)
                                         .setReconnectAttempts(10)
                                         .setReconnectInterval(1000);

        AmqpClient amqpClient = AmqpClient.create(vertx, amqpClientOptions);

        vertx.rxDeployVerticle(() -> new ReconnectSenderVerticle(amqpClient), new DeploymentOptions())
             .subscribe(
                     did -> log.info("Successfully started server"),
                     err -> log.error("Failed to start server", err)
             );
    }



    @Override
    public Completable rxStart() {
        return this.vertx.createHttpServer()
                         .requestHandler(this::sendMessage)
                         .rxListen(8080)
                         .ignoreElement();
    }



    private Single<AmqpSender> getOrRecreateSender() {
        if (this.sender != null) {
            if (!this.sender.connection().isDisconnected()) {
                return Completable.fromAction(() -> log.info("Sender still connected. Reusing it!"))
                                  .toSingleDefault(this.sender);

            } else {
                return Completable.fromAction(() -> log.info("Sender disconnected. Closing and reconnecting..."))
                                  // Problem with this is, that `AmqpSender.rxClose()` never completes. The reason is
                                  // that, even if `AmqpSenderImpl.close()` sets a `closeHandler` on the underlying
                                  // `ProtonSender`, the `ProtonSender` never calls that close handler. That's why
                                  // the rxClose never completes.
                                  .andThen(
                                          this.sender.rxClose()
                                                     .timeout(
                                                             1, TimeUnit.SECONDS,
                                                             RxHelper.scheduler(this.vertx.getDelegate())
                                                     )
                                                     .onErrorResumeNext(err -> {
                                                         log.warn("Closing sender failed. Continuing anyway", err);

                                                         return Completable.complete();
                                                     })
                                  )
                                  .doOnComplete(() -> log.info("Sender closed. Reconnecting..."))
                                  .andThen(createSender());
            }
        } else {
            return createSender();
        }
    }



    private Single<AmqpSender> createSender() {
        return Completable.fromAction(() -> log.info("Trying to create a new sender"))
                          .andThen(this.amqpClient.rxCreateSender("example_address"))
                          .retryWhen(errors -> errors.flatMap(err -> {
                              log.warn("Build-in reconnect failed. Trying again in 1000ms", err);

                              return Flowable.timer(
                                      1, TimeUnit.SECONDS,
                                      RxHelper.scheduler(this.vertx.getDelegate())
                              );
                          }))
                          .doOnSuccess(sender -> {
                              log.info("Successfully created sender");
                              this.sender = sender;
                          });
    }



    public void sendMessage(HttpServerRequest request) {
        Completable.fromAction(() -> log.info("Trying to send message to message broker"))
                   .andThen(this.getOrRecreateSender())
                   .flatMapCompletable(
                           sender -> sender.rxSendWithAck(
                                                   AmqpMessageBuilder.create()
                                                                     .withBody("a message body")
                                                                     .build()
                                           )
                                           .timeout(
                                                   3, TimeUnit.SECONDS,
                                                   RxHelper.scheduler(this.vertx.getDelegate())
                                           )
                   )
                   .retryWhen(errors -> {
                       AtomicInteger counter = new AtomicInteger();

                       return errors
                               .takeWhile(e -> counter.getAndIncrement() != 3)
                               .flatMap(err -> {
                                   log.warn("Sending failed. Trying again in 1000ms", err);

                                   return Flowable.timer(
                                           1, TimeUnit.SECONDS,
                                           RxHelper.scheduler(this.vertx.getDelegate())
                                   );
                               });
                   })
                   .subscribe(
                           () -> {
                               log.info("Successfully send message");
                               request.response().end("Successfully send message :)");
                           },
                           err -> {
                               log.error("Failed to send message", err);
                               request.response().setStatusCode(500).end("Failed to send message");
                           }
                   );
    }
}
