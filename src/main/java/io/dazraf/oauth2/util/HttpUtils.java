package io.dazraf.oauth2.util;

import io.vertx.ext.web.RoutingContext;

public class HttpUtils {
  public static String checkPresent(RoutingContext context, String parameter) throws Exception {
    String value = context.request().getParam(parameter);
    if (value == null) {
      String msg = "the request is missing parameter: " + parameter;
      httpBadRequest(context, msg);
      throw new Exception(msg);
    }
    return value;
  }

  public static String checkEquals(RoutingContext context, String parameter, String expectedValue) throws Exception {
    String value = context.request().getParam(parameter);
    if (value == null || !value.equals(expectedValue)) {
      String msg =  "the request parameter: " + parameter + " should be " + expectedValue;
      httpBadRequest(context, msg);
      throw new Exception(msg);
    }
    return value;
  }

  public static void httpBadRequest(RoutingContext context, String errorMessage) {
    context.response().setStatusCode(400).setStatusMessage(errorMessage).end();
  }

  public static void httpRedirectTemporary(RoutingContext context, String redirectURI) {
    context.response().setStatusCode(303).putHeader("Location", redirectURI).end();
  }

}
