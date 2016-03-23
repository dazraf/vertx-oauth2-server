package io.dazraf.oauth2.authprovider;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

import java.util.HashMap;
import java.util.Map;

public class InMemoryAuthProvider implements AuthProvider {
  private final Map<String, User> users = new HashMap<>();

  public InMemoryAuthProvider(JsonArray users) {
    users.forEach(obj -> {
      JsonObject user = (JsonObject)obj;
      String username = user.getString("username");
      this.users.put(username, new InMemoryUser(user));
    });
  }


  public static InMemoryAuthProvider create(JsonArray users) {
    return new InMemoryAuthProvider(users);
  }

  @Override
  public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
    String username = authInfo.getString("username");
    User user = users.get(username);
    if (user == null) {
      resultHandler.handle(Future.failedFuture("couldn't find username: " + username));
      return;
    }
    String password = user.principal().getString("password", "");
    if (!authInfo.getString("password", "").equals(password)) {
      resultHandler.handle(Future.failedFuture("incorrect password"));
    } else {
      resultHandler.handle(Future.succeededFuture(user));
    }
  }

  public static class InMemoryUser extends AbstractUser {
    private final JsonObject user;

    public InMemoryUser(JsonObject user) {
      this.user = user;
    }

    @Override
    protected void doIsPermitted(String permission, Handler<AsyncResult<Boolean>> resultHandler) {
      resultHandler.handle(Future.succeededFuture(true));
    }

    @Override
    public JsonObject principal() {
      return user;
    }

    @Override
    public void setAuthProvider(AuthProvider authProvider) {
    }
  }
}
