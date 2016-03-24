package io.dazraf.oauth2.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.context.FieldValueResolver;
import com.github.jknack.handlebars.context.JavaBeanValueResolver;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.context.MethodValueResolver;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;

final public class HandlebarUtils {
  private static final  ObjectMapper objectMapper = new ObjectMapper();
  public static Handlebars handlebarWithJson() {
    Handlebars handlebars = new Handlebars();
    handlebars.registerHelper("json", Jackson2Helper.INSTANCE);
    return handlebars;
  }

  public static String applyTemplate(Template template, Object json) throws IOException {
    final JsonNode jsonNode = objectMapper.readTree(json.toString());
    Context handlebarContext = Context
      .newBuilder(jsonNode)
      .resolver(JsonNodeValueResolver.INSTANCE,
        JavaBeanValueResolver.INSTANCE,
        FieldValueResolver.INSTANCE,
        MapValueResolver.INSTANCE,
        MethodValueResolver.INSTANCE
      )
      .build();

    return template.apply(handlebarContext);
  }

  public static void renderJsonWithTemplate(RoutingContext context, Template template, JsonObject json) throws IOException {
    context.response().putHeader("Content-Type", "text/html").end( applyTemplate(template, json));
  }

}
