package io.dazraf.oauth2.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.CompositeFutureImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static java.util.stream.StreamSupport.stream;

public class FutureChain<T> implements Future<T> {
  private static final Logger LOG = LoggerFactory.getLogger(FutureChain.class);
  private final Future<T> internalFuture;
  private final FutureChain parent; // required to avoid the parent being GC'd
  private List<Consumer<Future<T>>> dependents = new LinkedList<>();

  private FutureChain(Future<T> future, FutureChain parent) {
    this.internalFuture = future;
    this.parent = parent;
    future.setHandler(asyncResult -> {
      notifyAllDependents(future);
    });
  }

  private synchronized void notifyAllDependents(Future<T> future) {
    dependents.forEach(dependent -> {
      try {
        dependent.accept(future);
      } catch (Throwable throwable) {
        LOG.error("during forwarding of a complete future to dependent", throwable);
      }
    });
  }

  /**
   * General purpose wrapper around any future to create a chainable sequence
   *
   * @param future
   * @param <Output>
   * @return
   */
  public static <Output> FutureChain<Output> when(Future<Output> future) {
    return new FutureChain<>(future, null);
  }


  public static <T> Future<List<T>> join(Future<T>... args) {
    return join(Arrays.asList(args));
  }

  @SuppressWarnings("unchecked")
  public static <T> Future<List<T>> join(List<Future<T>> futures) {
    if (futures.size() == 0) {
      return Future.succeededFuture(emptyList());
    }
    Future<List<T>> result = Future.future();
    final Future[] arrayFutures = futures.toArray(new Future[futures.size()]);
    when(CompositeFutureImpl.<T>all(arrayFutures))
      .onSuccess(cf -> {
        result.complete(
          range(0, cf.size())
            .mapToObj(cf::result)
            .map(item -> (T) item)
            .collect(toList()));
      })
      .onError(result::fail);
    return result;
  }

  /**
   * Given a successful result, call the supplier function that returns a new Future.
   * Errors from the new future are propagated.
   *
   * @param supplier
   * @param <T2>
   * @return
   */
  public <T2> FutureChain<T2> then(Supplier<Future<T2>> supplier) {
    Future<T2> future = Future.future();
    addHandler(response -> {
      if (response.succeeded()) {
        supplier.get().setHandler(future.completer());
      } else {
        future.fail(response.cause());
      }
    });
    return new FutureChain<>(future, this);
  }

  /**
   * Given a successful result, pass it to a function that generates a new Future.
   * Errors from the new future are propagated.
   *
   * @param function
   * @param <T2>
   * @return
   */
  public <T2> FutureChain<T2> then(Function<T, Future<T2>> function) {
    Future<T2> future = Future.future();
    addHandler(response -> {
      if (response.succeeded()) {
        try {
          function.apply(response.result()).setHandler(future.completer());
        } catch (Throwable throwable) {
          LOG.error("failed to call consumer", throwable);
          future.fail(throwable);
        }
      } else {
        future.fail(response.cause());
      }
    });
    return new FutureChain<>(future, this);
  }

  /**
   * general purpose error handler
   *
   * @param errorConsumer
   * @return
   */
  public FutureChain<T> onError(Consumer<Throwable> errorConsumer) {
    Future<T> future = Future.future();
    addHandler(response -> {
      if (response.failed()) {
        try {
          errorConsumer.accept(response.cause());
          future.fail(response.cause());
        } catch (Throwable throwable) {
          LOG.error("failed to call error consumer", throwable);
          future.fail(throwable);
        }
      } else {
        future.complete(response.result());
      }
    });
    return new FutureChain<>(future, this);
  }

  /**
   * This function allows for an alternative asynchronous execution path in the event of a failure
   *
   * @param alternative
   * @return
   */
  public FutureChain<T> otherwise(Function<Throwable, Future<T>> alternative) {
    Future<T> future = Future.future();
    addHandler(response -> {
      try {
        if (response.failed()) {
          alternative.apply(response.cause()).setHandler(future.completer());
        } else {
          future.complete(response.result());
        }
      } catch (Throwable throwable) {
        LOG.error("failed to call supplier", throwable);
        future.fail(throwable);
      }
    });
    return new FutureChain<>(future, this);
  }

  /**
   * General purpose handler for success. Any errors from the runnable are propogated
   *
   * @param runnable
   * @return
   */
  public FutureChain<T> onSuccess(Runnable runnable) {
    Future<T> future = Future.future();
    addHandler(response -> {
      if (response.succeeded()) {
        try {
          runnable.run();
        } catch (Throwable throwable) {
          LOG.error("failed to call consumer", throwable);
        }
        future.complete(response.result());
      } else {
        future.fail(response.cause());
      }
    });
    return new FutureChain<>(future, this);
  }

  /**
   * General purpose handler on success. Any errors from the consumer are propagated.
   *
   * @param consumer
   * @return
   */
  public FutureChain<T> onSuccess(Consumer<T> consumer) {
    Future<T> future = Future.future();
    addHandler(response -> {
      if (response.succeeded()) {
        try {
          consumer.accept(response.result());
        } catch (Throwable throwable) {
          LOG.error("failed to call consumer", throwable);
        }
        future.complete(response.result());
      } else {
        future.fail(response.cause());
      }
    });
    return new FutureChain<>(future, this);
  }


  /**
   * Bind to all response, success or otherise and passes the resolved future to the consumer
   *
   * @param consumer a future consumer
   * @return
   */
  public FutureChain<T> onResponse(Consumer<Future<T>> consumer) {
    Future<T> future = Future.future();
    addHandler(response -> {
      if (response.succeeded()) {
        try {
          consumer.accept(response);
        } catch (Throwable throwable) {
          LOG.error("failed to call consumer", throwable);
        }
        future.complete(response.result());
      } else {
        future.fail(response.cause());
      }
    });
    return new FutureChain<>(future, this);
  }

  /**
   * Special operator to map to Void
   *
   * @return
   */
  public FutureChain<Void> mapVoid() {
    Future<Void> future = Future.future();
    addHandler(response -> {
      if (response.succeeded()) {
        try {
          future.complete();
        } catch (Throwable throwable) {
          LOG.error(throwable.getMessage(), throwable);
          future.fail(throwable);
        }
      } else {
        future.fail(response.cause());
      }
    });
    return new FutureChain<>(future, this);

  }

  /**
   * On success map the result to a new type using a provided mapping function. Any errors in the mapper are propagated.
   *
   * @param mapper
   * @param <T2>
   * @return
   */
  public <T2> FutureChain<T2> map(Function<T, T2> mapper) {
    Future<T2> future = Future.future();
    addHandler(response -> {
      if (response.succeeded()) {
        try {
          future.complete(mapper.apply(response.result()));
        } catch (Throwable throwable) {
          LOG.error(throwable.getMessage(), throwable);
          future.fail(throwable);
        }
      } else {
        future.fail(response.cause());
      }
    });
    return new FutureChain<>(future, this);
  }

  /**
   * Peek at a successful result. Errors in the consumer are not propagated.
   *
   * @param consumer
   * @return
   */
  public FutureChain<T> peekSuccess(Consumer<T> consumer) {
    addHandler(response -> {
      try {
        if (response.succeeded()) {
          consumer.accept(response.result());
        }
      } catch (Throwable throwable) {
        LOG.error("peek function threw an exception", throwable);
      }
    });
    return this;
  }


  /**
   * Peek at any errors. Further errors from the consumer are not propagated.
   *
   * @param consumer
   * @return
   */
  public FutureChain<T> peekError(Consumer<Throwable> consumer) {
    addHandler(response -> {
      try {
        if (response.failed()) {
          consumer.accept(response.cause());
        }
      } catch (Throwable throwable) {
        LOG.error("peek function threw an exception", throwable);
      }
    });
    return this;
  }

  public FutureChain<T> peek(Consumer<AsyncResult<T>> consumer) {
    addHandler(response -> {
      try {
        consumer.accept(response);
      } catch (Throwable throwable) {
        LOG.error("peek function threw an exception", throwable);
      }
    });
    return this;
  }

  private synchronized void addHandler(Consumer<Future<T>> handler) {
    dependents.add(handler);
    if (this.internalFuture.isComplete()) {
      handler.accept(internalFuture);
    }
  }

  public FutureChain<T> completeOn(Handler<AsyncResult<T>> callback) {
    Future<T> future = Future.future();
    addHandler(response -> {
      try {
        callback.handle(response);
        future.completer().handle(response);
      } catch (Throwable throwable) {
        LOG.error("failed to call callback", throwable);
        future.fail(throwable);
      }
    });
    return new FutureChain<>(future, this);
  }

  @SuppressWarnings("unchecked")
  public <Item, Result> FutureChain<List<Result>> flatMap(Function<Item, Future<Result>> function) {
    Future<List<Result>> future = Future.future();
    addHandler(response -> {
      if (response.succeeded()) {
        try {
          if (!(response.result() instanceof Iterable)) {
            future.fail("was expecting an Iterable but found: " + response.result().getClass().getName());
            return;
          }
          Iterable<Item> items = (Iterable<Item>) response.result();
          final List<Future<Result>> futureResults = stream(items.spliterator(), false)
            .map(function::apply)
            .collect(toList());
          when(join(futureResults))
            .completeOn(future.completer());
        } catch (Throwable throwable) {
          LOG.error(throwable.getMessage(), throwable);
          future.fail(throwable);
        }
      } else {
        future.fail(response.cause());
      }
    });
    return new FutureChain<>(future, this);
  }

  @Override
  public boolean isComplete() {
    return internalFuture.isComplete();
  }

  @Override
  public Future<T> setHandler(Handler<AsyncResult<T>> handler) {
    addHandler(handler::handle);
    return this;
  }

  @Override
  public void complete(T result) {
    internalFuture.complete(result);
  }

  @Override
  public void complete() {
    internalFuture.complete();
  }

  @Override
  public void fail(Throwable throwable) {
    internalFuture.fail(throwable);
  }

  @Override
  public void fail(String failureMessage) {
    internalFuture.fail(failureMessage);
  }

  @Override
  public T result() {
    return internalFuture.result();
  }

  @Override
  public Throwable cause() {
    return internalFuture.cause();
  }

  @Override
  public boolean succeeded() {
    return internalFuture.succeeded();
  }

  @Override
  public boolean failed() {
    return internalFuture.failed();
  }
}