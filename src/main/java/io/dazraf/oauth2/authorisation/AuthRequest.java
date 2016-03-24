package io.dazraf.oauth2.authorisation;

import io.vertx.ext.web.RoutingContext;

import static io.dazraf.oauth2.util.HttpUtils.mustGetRequestParam;
import static io.dazraf.oauth2.util.HttpUtils.mustGetRequestParamAndEquals;

class AuthRequest {

  private final String clientID;
  private final String redirectURI;
  private final String[] scopes;
  private final String responseType;

  public static AuthRequest create(RoutingContext context) throws Exception {
    return new AuthRequest(context);
  }

  private AuthRequest(RoutingContext context) throws Exception {
    this.clientID = mustGetRequestParam(context, "client_id");
    this.redirectURI = mustGetRequestParam(context, "redirect_uri");
    this.scopes = mustGetRequestParam(context, "scope").split("\\s+");
    // we currently on support code auth grant response_type requests
    // this means the application (the merchant etc) has to swap the grant out for the access code ...
    this.responseType = mustGetRequestParamAndEquals(context, "response_type", "code");
  }

  public String getClientID() {
    return clientID;
  }

  public String getRedirectURI() {
    return redirectURI;
  }


  public String[] getScopes() {
    return scopes;
  }

  public String getResponseType() {
    return responseType;
  }
}
