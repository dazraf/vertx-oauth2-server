package io.dazraf.oauth2.authorisation;

import java.math.BigInteger;
import java.security.SecureRandom;

class TokenFountain {
  private SecureRandom random = new SecureRandom();
  public String nextGrantCode() {
    return new BigInteger(32, random).toString(32);
  }
}
