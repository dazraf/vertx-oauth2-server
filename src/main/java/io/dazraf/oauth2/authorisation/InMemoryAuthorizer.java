package io.dazraf.oauth2.authorisation;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
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

  private final Map<String, String> grants = new HashMap<>();
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
      final AuthRequest authRequest = AuthRequest.create(context);

      // check that we know this client
      if (!registeredClients.containsKey(authRequest.getClientID())) {
        httpBadRequest(context, "unknown client id: " + authRequest.getClientID());
        return;
      }

      final List<String> notAuthorisedScopes = retrieveUnauthorisedScopes(authRequest);

      if (notAuthorisedScopes.size() > 0) {
        // we have to request authorisation for these ..
        requestResourceOwnerAuth(context, authRequest, notAuthorisedScopes);
      } else {
        respondWithGrant(context, authRequest);
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
      AuthRequest authRequest = AuthRequest.create(context);
      if (approved == null || !approved.equals("Yes")) {
        respondWithAccessDeniedError(context, authRequest);
        return;
      }

      addAuthorisedScopes(authRequest);

      respondWithGrant(context, authRequest);

    } catch (Throwable e) {
      LOG.error(e.getMessage(), e);
      httpBadRequest(context, "failed to apply authorization. See server logs");
    }
  }

  public void token(RoutingContext routingContext) {

  }


  private List<String> retrieveUnauthorisedScopes(AuthRequest authRequest) {
    return Stream.of(authRequest.getScopes())
          .map(scope -> Authorisation.create(authRequest.getClientID(), scope))
          .filter(authorisation -> !authorisations.contains(authorisation))
          .map(Authorisation::getScope)
          .collect(toList());
  }

  private void requestResourceOwnerAuth(RoutingContext context, AuthRequest request, List<String> notAuthorisedScopes) {
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


  private void respondWithGrant(RoutingContext context, AuthRequest authRequest) {
    String code = tokenFountain.nextGrantCode();
    grants.put(code, authRequest.getClientID());
    context.vertx().setTimer(5_000, id -> removeGrant(code));

    final String state = context.request().getParam("state");
    Map<String, String> params = new HashMap<>();
    params.put("code", code);
    if (state != null)
    params.put("state", state);
    httpRedirectTemporary(context, authRequest.getRedirectURI() + buildPathParams(params));
  }


  private void respondWithAccessDeniedError(RoutingContext context, AuthRequest authRequest) {
    httpRedirectTemporary(context, authRequest.getRedirectURI() + "?error=access_denied");
  }

  private void addAuthorisedScopes(AuthRequest authRequest) {
    retrieveUnauthorisedScopes(authRequest).stream()
      .forEach(scope -> {
        Authorisation authorisation = Authorisation.create(authRequest.getClientID(), scope);
        authorisations.add(authorisation);
      });
  }

  private void removeGrant(String code) {
    final String client = grants.get(code);
    LOG.info("grant {} for client {} expired", code, client != null ? client : "unknown client!" );
    grants.remove(code);
  }

}
