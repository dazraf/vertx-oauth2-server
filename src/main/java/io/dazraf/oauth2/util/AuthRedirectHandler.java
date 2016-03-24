package io.dazraf.oauth2.util;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.impl.AuthHandlerImpl;

public class AuthRedirectHandler extends AuthHandlerImpl {

  private static final Logger log = LoggerFactory.getLogger(AuthRedirectHandler.class);
  /**
   * Default path the user will be redirected to
   */
  private final static String DEFAULT_LOGIN_REDIRECT_URL = "/loginpage";

  /**
   * Default name of param used to store return url information in session
   */
  private final static String DEFAULT_RETURN_URL_PARAM = "return_url";


  private final String loginRedirectURL;
  private final String returnURLParam;

  /**
   * Create a handler
   *
   * @param authProvider  the auth service to use
   * @return the handler
   */
  public static AuthHandler create(AuthProvider authProvider) {
    return new AuthRedirectHandler(authProvider, DEFAULT_LOGIN_REDIRECT_URL, DEFAULT_RETURN_URL_PARAM);
  }

  /**
   * Create a handler
   *
   * @param authProvider  the auth service to use
   * @param loginRedirectURL  the url to redirect the user to
   * @return the handler
   */
  public static AuthHandler create(AuthProvider authProvider, String loginRedirectURL) {
    return new AuthRedirectHandler(authProvider, loginRedirectURL, DEFAULT_RETURN_URL_PARAM);
  }

  /**
   * Create a handler
   *
   * @param authProvider  the auth service to use
   * @param loginRedirectURL  the url to redirect the user to
   * @param returnURLParam  the name of param used to store return url information in session
   * @return the handler
   */
  static AuthHandler create(AuthProvider authProvider, String loginRedirectURL, String returnURLParam) {
    return new AuthRedirectHandler(authProvider, loginRedirectURL, returnURLParam);
  }

  private AuthRedirectHandler(AuthProvider authProvider, String loginRedirectURL, String returnURLParam) {
    super (authProvider);
    this.loginRedirectURL = loginRedirectURL;
    this.returnURLParam = returnURLParam;
  }

  @Override
  public void handle(RoutingContext context) {
    Session session = context.session();
    if (session != null) {
      User user = context.user();
      if (user != null) {
        // Already logged in, just authorise
        authorise(user, context);
      } else {
        // Now redirect to the login url - we'll get redirected back here after successful login
        session.put(returnURLParam, context.request().absoluteURI());
        context.response().putHeader("location", loginRedirectURL).setStatusCode(302).end();
      }
    } else {
      context.fail(new NullPointerException("No session - did you forget to include a SessionHandler?"));
    }

  }
}