package io.dazraf.oauth2;

import io.dazraf.oauth2.authprovider.InMemoryAuthProvider;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.vertx.ext.web.Router.router;

public class OAuth2ServerVerticle extends AbstractVerticle {
  private static final Logger LOG = LoggerFactory.getLogger(OAuth2ServerVerticle.class);

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(OAuth2ServerVerticle.class.getName());
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    int port = config().getInteger("port", 8080);
    String base = config().getString("baseURI", "/oauth2");
    String loginURL = config().getString("loginURL", base + "/login.html");

    AuthProvider authProvider = createAuthProvider();
    final AuthHandler authHandler = RedirectAuthHandler.create(authProvider, loginURL);
    final StaticHandler staticHandler = StaticHandler.create("oauth2-server-web");

    final Router router = router(vertx);
    setupCoreWebHandlers(authProvider, router);
    setupLoginHandlers(base, authProvider, router);

    // all requests to URI starting '/api/' require login
    router.route(base + "/api/*").handler(authHandler);

    // implement OAuth2 authorize
    router.get(base + "/api/authorize").handler(this::authorize);

    // bind OAuth2 token swap
    router.get(base + "/api/token").handler(this::token);

    // bind static handler
    router.get(base + "/*").handler(staticHandler);

    // and index html routing
    router.get(base).handler(context -> {
      context.response().putHeader("location", base + "/index.html").setStatusCode(302).end();
    });

    // start it up
    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(port, asyncResult -> {
        if (asyncResult.succeeded()) {
          LOG.info("started on http://localhost:{}{}", port, base);
          startFuture.complete();
        } else {
          LOG.error("failed to startup", asyncResult.cause());
          startFuture.fail(asyncResult.cause());
        }
      });
  }

  private void setupLoginHandlers(String base, AuthProvider authProvider, Router router) {
    // bind login
    router.route(base + "/login").handler(FormLoginHandler.create(authProvider));
    // bind logout
    router.route(base + "/logout").handler(context -> {
      context.clearUser();
      // Redirect back to the index page
      context.response().putHeader("location", base + "/index.html").setStatusCode(302).end();
    });
  }

  private void setupCoreWebHandlers(AuthProvider authProvider, Router router) {
    router.route().handler(CookieHandler.create());
    router.route().handler(BodyHandler.create());
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    router.route().handler(UserSessionHandler.create(authProvider));
  }

  private void authorize(RoutingContext routingContext) {
    routingContext.response().end("TODO");
  }

  private void token(RoutingContext routingContext) {
    routingContext.response().end("TODO");
  }

  private AuthProvider createAuthProvider() {
    JsonArray users = config().getJsonArray("users");
    if (users == null) {
      users = createDefaultUsers();
    }
    return InMemoryAuthProvider.create(users);
  }

  private JsonArray createDefaultUsers() {
    return new JsonArray()
      .add(createUser("fuzz", "fuzz"))
      .add(createUser("robin", "robin"))
      .add(createUser("nick", "nick"))
      .add(createUser("james", "james"))
      .add(createUser("tony", "tony"))
      .add(createUser("john", "john"));
  }

  private JsonObject createUser(String username, String password) {
    return new JsonObject().put("username", username).put("password", password);
  }

}
