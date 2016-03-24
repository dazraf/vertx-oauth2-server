package io.dazraf.oauth2.authorisation;

class Authorisation {
  private String clientID;
  private String scope;

  Authorisation(String clientID, String scope) {
    this.clientID = clientID;
    this.scope = scope;
  }

  public String getClientID() {
    return clientID;
  }

  public String getScope() {
    return scope;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Authorisation that = (Authorisation) o;

    if (clientID != null ? !clientID.equals(that.clientID) : that.clientID != null) return false;
    return scope != null ? scope.equals(that.scope) : that.scope == null;

  }

  @Override
  public int hashCode() {
    int result = clientID != null ? clientID.hashCode() : 0;
    result = 31 * result + (scope != null ? scope.hashCode() : 0);
    return result;
  }
}
