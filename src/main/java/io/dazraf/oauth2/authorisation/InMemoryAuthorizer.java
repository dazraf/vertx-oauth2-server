package io.dazraf.oauth2.authorisation;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.dazraf.oauth2.util.HandlebarUtils.handlebarWithJson;
import static io.dazraf.oauth2.util.HandlebarUtils.renderJsonWithTemplate;
import static io.dazraf.oauth2.util.HttpUtils.buildPathParams;
import static io.dazraf.oauth2.util.HttpUtils.httpBadRequest;
import static io.dazraf.oauth2.util.HttpUtils.httpRedirectTemporary;
import static io.dazraf.oauth2.util.MapUtils.toJsonObject;
import static java.util.stream.Collectors.toList;

public class InMemoryAuthorizer {
  private static final Logger LOG = LoggerFactory.getLogger(InMemoryAuthorizer.class);

  private final Handlebars handlebars = handlebarWithJson();
  private final Template authTemplate;

  private final TokenFountain tokenFountain = new TokenFountain();

  private final JsonObject registeredClients;

  private final JsonObject scopes;

  private final Set<Authorisation> authorisations = new HashSet<>();

  private final Map<String, GrantRequest> grants = new HashMap<>();

  private final Map<String, AccessRequest> accessTokens = new HashMap<>();
  private final String basePath;

  public static InMemoryAuthorizer create(String basePath, JsonObject clients, JsonObject scopes) throws IOException {
    return new InMemoryAuthorizer(basePath, clients, scopes);
  }

  private InMemoryAuthorizer(String basePath, JsonObject clients, JsonObject scopes) throws IOException {
    this.registeredClients = clients;
    this.scopes = scopes;
    this.basePath = basePath;
    authTemplate = handlebars.compile("oauth2-server-web/templates/authorise");
  }

  public void reset(RoutingContext context) {
    authorisations.clear();
    grants.clear();
    httpRedirectTemporary(context, basePath);
  }

  public void authorize(RoutingContext context) {

    try {
      final GrantRequest grantRequest = GrantRequest.create(context);

      // check that we know this client
      if (!registeredClients.containsKey(grantRequest.getClientID())) {
        httpBadRequest(context, "unknown client id: " + grantRequest.getClientID());
        return;
      }

      final List<String> notAuthorisedScopes = retrieveUnauthorisedScopes(grantRequest);

      if (notAuthorisedScopes.size() > 0) {
        // we have to request authorisation for these ..
        requestResourceOwnerAuth(context, grantRequest, notAuthorisedScopes);
      } else {
        respondWithGrant(context, grantRequest);
      }
    } catch (Throwable e) {
      LOG.error(e.getMessage(), e);
      httpBadRequest(context, "failed to authorize. See server logs");
    }
  }

  public void approveAuth(RoutingContext context) {
    // we've just received an approval ... awesome
    try {

      String approved = context.request().getParam("approved");
      GrantRequest grantRequest = GrantRequest.create(context);
      if (approved == null || !approved.equals("Yes")) {
        respondWithAccessDeniedError(context, grantRequest);
        return;
      }

      addAuthorisedScopes(grantRequest);

      respondWithGrant(context, grantRequest);

    } catch (Throwable e) {
      LOG.error(e.getMessage(), e);
      httpBadRequest(context, "failed to apply authorization. See server logs");
    }
  }

  public void token(RoutingContext context) {
    try {
      final AccessRequest accessRequest = AccessRequest.create(context);
      GrantRequest grant = grants.get(accessRequest.getCode());
      if (grant == null) {
        String err = "could not find the access code " + accessRequest.getCode();
        LOG.error(err);
        respondAccessTokenError(context, createAccessTokenErrorPayload("invalid_grant", err));
        return;
      }

      if (!accessRequest.getClientID().equals(grant.getClientID())) {
        String err = "client id " + accessRequest.getClientID() + " does not match original auth client id " + grant.getClientID();
        LOG.error(err);
        respondAccessTokenError(context, createAccessTokenErrorPayload("invalid_client", err));
        return;
      }

      if (!accessRequest.getRedirectedURI().equals(grant.getRedirectURI())) {
        String err = "redirect_uri " + accessRequest.getRedirectedURI() + " does not match original auth redirect_uri " + grant.getRedirectURI();
        LOG.error(err);
        respondAccessTokenError(context, createAccessTokenErrorPayload("invalid_grant", err));
        return;
      }

      if (!accessRequest.getGrantType().equals("authorization_code")) {
        String err = "grant_type " + accessRequest.getGrantType() + " must be authorization_code";
        LOG.error(err);
        respondAccessTokenError(context, createAccessTokenErrorPayload("unsupported_grant_type", err));
        return;
      }

      final String accessToken = tokenFountain.nextAccessToken();
      JsonObject response = new JsonObject();
      response.put("access_token", accessToken)
        .put("token_type", "bearer")
        .put("expires_in", 3600)
        .put("scope", grant.getScope());

      accessTokens.put(accessToken, accessRequest);
      context.response().putHeader("Cache-Control", "no-store").putHeader("Pragma", "no-cache")
        .putHeader("Content-Type", "application/json")
      .end(response.encodePrettily());

      // we've now expended this grant
      grants.remove(accessRequest.getCode());

      context.vertx().setTimer(3600 * 1000, id -> {
        LOG.info("access token {} expired for client {}", accessToken, accessRequest.getClientID());
        accessTokens.remove(accessToken);
      });


    } catch (Throwable e) {
      String err = e.getMessage();
      LOG.error(e.getMessage(), e);
      respondAccessTokenError(context, createAccessTokenErrorPayload("invalid_request", err));
    }
  }

  private JsonObject createAccessTokenErrorPayload(String errorCode, String description) {
    return new JsonObject().put("error", errorCode).put("error_description", description);
  }

  private void respondAccessTokenError(RoutingContext context, JsonObject error) {
    context.response().putHeader("Content-Type", "application/json").setStatusCode(400).end(error.encodePrettily());
  }


  private List<String> retrieveUnauthorisedScopes(GrantRequest grantRequest) {
    return Stream.of(grantRequest.getScopes())
      .map(scope -> Authorisation.create(grantRequest.getClientID(), scope))
      .filter(authorisation -> !authorisations.contains(authorisation))
      .map(Authorisation::getScope)
      .collect(toList());
  }

  private void requestResourceOwnerAuth(RoutingContext context, GrantRequest request, List<String> notAuthorisedScopes) {
    // get a list of descriptions for the scopes being requested

    try {
      List<String> scopeDescriptions = notAuthorisedScopes.stream()
        .map(scope -> scopes.getJsonObject(scope).getString("description"))
        .collect(toList());

      JsonObject result = new JsonObject()
        .put("client", registeredClients.getJsonObject(request.getClientID()).getString("name"))
        .put("scope-descriptions", new JsonArray(scopeDescriptions))
        .put("query", toJsonObject(context.request().params()));

      renderJsonWithTemplate(context, authTemplate, result);
    } catch (Throwable e) {
      LOG.error("failed to render auth request page", e);
      httpBadRequest(context, "failed to render auth request page");
    }

  }


  private void respondWithGrant(RoutingContext context, GrantRequest grantRequest) {
    String code = tokenFountain.nextGrantCode();
    grants.put(code, grantRequest);
    context.vertx().setTimer(3600 * 1000, id -> removeGrant(code));

    final String state = context.request().getParam("state");
    Map<String, String> params = new HashMap<>();
    params.put("code", code);
    if (state != null)
      params.put("state", state);
    httpRedirectTemporary(context, grantRequest.getRedirectURI() + buildPathParams(params));
  }


  private void respondWithAccessDeniedError(RoutingContext context, GrantRequest grantRequest) {
    httpRedirectTemporary(context, grantRequest.getRedirectURI() + "?error=access_denied");
  }

  private void addAuthorisedScopes(GrantRequest grantRequest) {
    retrieveUnauthorisedScopes(grantRequest).stream()
      .forEach(scope -> {
        Authorisation authorisation = Authorisation.create(grantRequest.getClientID(), scope);
        authorisations.add(authorisation);
      });
  }

  private void removeGrant(String code) {
    final GrantRequest request = grants.get(code);
    LOG.info("grant {} for client {} expired", code, request != null ? request.getClientID() : "unknown client!");
    grants.remove(code);
  }

}
