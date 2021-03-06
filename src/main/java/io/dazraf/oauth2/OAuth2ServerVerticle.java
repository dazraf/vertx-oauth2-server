package io.dazraf.oauth2;

import io.dazraf.oauth2.authentication.InMemoryAuthenticationProvider;
import io.dazraf.oauth2.authorisation.InMemoryAuthorizer;
import io.dazraf.oauth2.util.AuthRedirectHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static io.vertx.ext.web.Router.router;

public class OAuth2ServerVerticle extends AbstractVerticle {
  private static final Logger LOG = LoggerFactory.getLogger(OAuth2ServerVerticle.class);

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(OAuth2ServerVerticle.class.getName());
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    // resolve the config
    JsonObject config = getConfigObject();
    LOG.info("running with config: {}", config.toString());

    // get the config fields for the paths
    int port = config.getInteger("port", 8080);
    String base = config.getString("basePath", "/oauth2");
    String loginURL = config.getString("loginURL", base + "/login.html");
    String apiPath = base + config.getString("apiPath", "/api");

    // create the handlers
    final AuthProvider authProvider = createAuthProvider(config);
    final AuthHandler authHandler = AuthRedirectHandler.create(authProvider, loginURL);
    final StaticHandler staticHandler = StaticHandler.create("oauth2-server-web");
    final InMemoryAuthorizer authorizer = InMemoryAuthorizer.create(
      base,
      config.getJsonObject("clients"),
      config.getJsonObject("scopes"));

    // setup the router
    final Router router = router(vertx);

    setupCoreWebHandlers(authProvider, router);

    setupLoginHandlers(base, apiPath, authProvider, router);

    // auth protected paths
    router.route(apiPath + "/authorize").handler(authHandler);
    router.route(apiPath + "/approveauth").handler(authHandler);
    router.route(apiPath + "/reset").handler(authHandler);

    // bind api
    router.route(apiPath + "/authorize").handler(authorizer::authorize);
    router.route(apiPath + "/token").handler(authorizer::token);
    router.get(apiPath + "/approveauth").handler(authorizer::approveAuth);
    router.route(apiPath + "/tokeninfo").handler(authorizer::tokenInfo);
    router.get(apiPath + "/reset").handler(authorizer::reset);

    // and index html routing
    router.get(base).handler(context -> {
      context.response().putHeader("location", base + "/index.html").setStatusCode(302).end();
    });

    // bind static handler
    router.get(base + "/*").handler(staticHandler);

    // start it up
    HttpServerOptions serverOptions = new HttpServerOptions();
    serverOptions
      .setSsl(true)
      .setKeyStoreOptions(new JksOptions().setPath("jks/keystore.jks").setPassword("8a5500n"));

    vertx.createHttpServer(serverOptions)
      .requestHandler(router::accept)
      .listen(port, asyncResult -> {
        if (asyncResult.succeeded()) {
          LOG.info("started on https://localhost:{}{}", port, base);
          startFuture.complete();
        } else {
          LOG.error("failed to startup", asyncResult.cause());
          startFuture.fail(asyncResult.cause());
        }
      });
  }

  private JsonObject getConfigObject() throws IOException {
    JsonObject config = config();
    LOG.info("config not set. loading default.json");
    if (config == null || config.isEmpty()) {
      String text = IOUtils.toString(ClassLoader.getSystemClassLoader().getResourceAsStream("config/default.json"));
      config = new JsonObject(text);
    }
    return config;
  }

  private void setupLoginHandlers(String base, String apiPath, AuthProvider authProvider, Router router) {
    // bind login
    router.route(apiPath + "/login").handler(FormLoginHandler.create(authProvider));
    // bind logout
    router.route(apiPath + "/logout").handler(context -> {
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

  private AuthProvider createAuthProvider(JsonObject config) {
    return InMemoryAuthenticationProvider.create(config.getJsonObject("users"));
  }
}
