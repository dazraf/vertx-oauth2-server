package io.dazraf.oauth2.authorisation;

import com.github.jknack.handlebars.*;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.HandlebarsTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static io.dazraf.oauth2.util.HandlebarUtils.handlebarWithJson;
import static io.dazraf.oauth2.util.HandlebarUtils.renderJsonWithTemplate;
import static io.dazraf.oauth2.util.HttpUtils.httpBadRequest;
import static io.dazraf.oauth2.util.HttpUtils.httpRedirectTemporary;
import static io.dazraf.oauth2.util.MapUtils.toJsonObject;
import static java.util.stream.Collectors.toList;

public class InMemoryAuthorizer {
  private static final Logger LOG = LoggerFactory.getLogger(InMemoryAuthorizer.class);
  private final Handlebars handlebars = handlebarWithJson();
  private final Template authTemplate;
  private final TokenFountain tokenFountain = new TokenFountain();
  private final JsonObject clients;
  private final JsonObject scopes;
  private Set<Authorisation> authorisations = new HashSet<>();
  private HandlebarsTemplateEngine handleBars = HandlebarsTemplateEngine.create();

  public static InMemoryAuthorizer create(JsonObject clients, JsonObject scopes) throws IOException {
    return new InMemoryAuthorizer(clients, scopes);
  }

  private InMemoryAuthorizer(JsonObject clients, JsonObject scopes) throws IOException {
    this.clients = clients;
    this.scopes = scopes;
    authTemplate = handlebars.compile("oauth2-server-web/templates/authorise");
  }


  public void authorize(RoutingContext context) {
    final HttpServerRequest request = context.request();

    try {
      final AuthRequest authRequest = AuthRequest.create(context);

      // check that we know this client
      if (!clients.containsKey(authRequest.getClientID())) {
        httpBadRequest(context, "unknown client id: " + authRequest.getClientID());
        return;
      }

      final List<String> notAuthorisedScopes = retrieveUnauthorisedScopes(authRequest);

      if (notAuthorisedScopes.size() > 0) {
        // we have to request authorisation for these ..
        requestResourceOwnerAuth(context, authRequest, notAuthorisedScopes);
      } else {
        context.response().end("TODO");
        // create a token
        // redirect to the return URL
      }
    } catch (Throwable e) {
      LOG.error(e.getMessage(), e);
      httpBadRequest(context, "failed to authorize. See server logs");
    }
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
        .put("client", clients.getJsonObject(request.getClientID()).getString("name"))
        .put("scope-descriptions", new JsonArray(scopeDescriptions))
        .put("query", toJsonObject(context.request().params()));

      renderJsonWithTemplate(context, authTemplate, result);
    } catch (Throwable e) {
      LOG.error("failed to render auth request page", e);
      httpBadRequest(context, "failed to render auth request page");
    }

  }

  public void token(RoutingContext routingContext) {

  }

  public void approveAuth(RoutingContext context) {
    // we've just received an approval ... awesome
    try {
      AuthRequest authReqest = AuthRequest.create(context);
      authReqest.getRedirectURI();

      retrieveUnauthorisedScopes(authReqest).stream()
        .forEach(scope -> {
          Authorisation authorisation = Authorisation.create(authReqest.getClientID(), scope);
          authorisations.add(authorisation);
        });

      String code = tokenFountain.nextGrantCode();
      httpRedirectTemporary(context, authReqest.getRedirectURI() + "?code=" + code);
    } catch (Throwable e) {
      LOG.error(e.getMessage(), e);
      httpBadRequest(context, "failed to apply authorization. See server logs");
    }
  }
}
