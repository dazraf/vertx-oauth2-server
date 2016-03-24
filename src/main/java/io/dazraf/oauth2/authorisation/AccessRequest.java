package io.dazraf.oauth2.authorisation;

import io.vertx.ext.web.RoutingContext;

import static io.dazraf.oauth2.util.HttpUtils.mustGetRequestParam;
import static io.dazraf.oauth2.util.HttpUtils.mustGetRequestParamAndEquals;

public class AccessRequest {

  private final String clientID;
  private final String grantType;
  private final String redirectedURI;
  private final String code;

  public static AccessRequest create(RoutingContext context) throws Exception {
    return new AccessRequest(context);
  }

  public AccessRequest(RoutingContext context) throws Exception {
    this.clientID = mustGetRequestParam(context, "client_id");
    this.redirectedURI = mustGetRequestParam(context, "redirect_uri");
    this.code = mustGetRequestParam(context, "code");

    // we only support access using code grants
    this.grantType = mustGetRequestParam(context, "grant_type");
  }

  public String getClientID() {
    return clientID;
  }

  public String getGrantType() {
    return grantType;
  }

  public String getRedirectedURI() {
    return redirectedURI;
  }

  public String getCode() {
    return code;
  }
}
