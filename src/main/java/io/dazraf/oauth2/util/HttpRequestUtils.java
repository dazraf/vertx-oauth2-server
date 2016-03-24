package io.dazraf.oauth2.util;

import io.vertx.ext.web.RoutingContext;

public class HttpRequestUtils {
  public static String checkPresent(RoutingContext context, String parameter) throws Exception {
    String value = context.request().getParam(parameter);
    if (value == null) {
      String msg = "the request is missing parameter: " + parameter;
      badRequest(context, msg);
      throw new Exception(msg);
    }
    return value;
  }

  public static String checkEquals(RoutingContext context, String parameter, String expectedValue) throws Exception {
    String value = context.request().getParam(parameter);
    if (value == null || !value.equals(expectedValue)) {
      String msg =  "the request parameter: " + parameter + " should be " + expectedValue;
      badRequest(context, msg);
      throw new Exception(msg);
    }
    return value;
  }

  public static void badRequest(RoutingContext context, String errorMessage) {
    context.response().setStatusCode(400).setStatusMessage(errorMessage).end();
  }
}
