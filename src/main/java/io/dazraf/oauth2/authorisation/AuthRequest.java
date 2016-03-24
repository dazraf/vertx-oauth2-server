package io.dazraf.oauth2.authorisation;

import io.vertx.ext.web.RoutingContext;

import static io.dazraf.oauth2.util.HttpRequestUtils.checkEquals;
import static io.dazraf.oauth2.util.HttpRequestUtils.checkPresent;

class AuthRequest {

  private final String clientID;
  private final String redirectURI;
  private final String state;
  private final String[] scopes;
  private final String responseType;

  public static AuthRequest create(RoutingContext context) throws Exception {
    return new AuthRequest(context);
  }

  private AuthRequest(RoutingContext context) throws Exception {
    this.clientID = checkPresent(context, "client_id");
    this.redirectURI = checkPresent(context, "redirect_uri");
    this.state = checkPresent(context, "state");
    this.scopes = checkPresent(context, "scope").split("\\s+");
    // we currently on support code auth grant response_type requests
    // this means the application (the merchant etc) has to swap the grant out for the access code ...
    this.responseType = checkEquals(context, "response_type", "code");
  }

  public String getClientID() {
    return clientID;
  }

  public String getRedirectURI() {
    return redirectURI;
  }

  public String getState() {
    return state;
  }

  public String[] getScopes() {
    return scopes;
  }

  public String getResponseType() {
    return responseType;
  }
}
