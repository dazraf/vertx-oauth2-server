package io.dazraf.oauth2.util;

import io.vertx.ext.web.RoutingContext;

import java.util.Map;
import java.util.stream.Collectors;

public class HttpUtils {
  public static String mustGetRequestParam(RoutingContext context, String parameter) throws Exception {
    String value = context.request().getParam(parameter);
    if (value == null) {
      String msg = "the request is missing parameter: " + parameter;
      httpBadRequest(context, msg);
      throw new Exception(msg);
    }
    return value;
  }

  public static String mustGetRequestParamAndEquals(RoutingContext context, String parameter, String expectedValue) throws Exception {
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
    context.response()
      .setStatusCode(303)
      .putHeader("Location", redirectURI)
      .putHeader("Content-Type", "application/x-www-form-urlencoded")
      .end();
  }

  public static String buildPathParams(Map<String, String> map) {
    String path = map.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
    if (path != null && !path.isEmpty()) {
      return "?" + path;
    } else {
      return "";
    }
  }
}
