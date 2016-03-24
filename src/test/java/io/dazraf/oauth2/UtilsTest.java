package io.dazraf.oauth2;

import org.junit.Assert;
import org.junit.Test;

public class UtilsTest {
  @Test
  public void testScopeSplit() {
    String test = "fp   loyalty  ";
    final String[] split = test.split("\\s+");
    int count = split.length;
    Assert.assertEquals(2, count);
  }
}
