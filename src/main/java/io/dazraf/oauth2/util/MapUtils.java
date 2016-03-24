package io.dazraf.oauth2.util;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapUtils {

  /**
   * Convert a path params multimap to a map object of key to list structure
   * @param mmap the path params
   * @return the map object
   */
  public static JsonObject toJsonObject(MultiMap mmap) {
   JsonObject json = new JsonObject();

    mmap.forEach(entry -> {

      String key = entry.getKey();
      if (!json.containsKey(key)) {
        json.put(key, new JsonArray().add(entry.getValue()));
      } else {
        json.getJsonArray(key).add(entry.getValue());
      }
    });

    return json;
  }
}
