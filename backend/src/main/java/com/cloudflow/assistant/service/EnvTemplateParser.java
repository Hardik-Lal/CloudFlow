package com.cloudflow.assistant.service;

import com.cloudflow.environment.domain.EnvironmentVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a generated dotenv-style template: {@code KEY=value}, with secrets marked by a trailing
 * {@code # secret} comment. Invalid and reserved keys are dropped.
 */
final class EnvTemplateParser {

  private static final Pattern LINE =
      Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");
  private static final Pattern SECRET_MARKER =
      Pattern.compile("\\s+#\\s*secret\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern KEY = Pattern.compile(EnvironmentVariable.KEY_PATTERN);

  private EnvTemplateParser() {}

  record TemplateVariable(String key, String value, boolean secret) {}

  static List<TemplateVariable> parse(String template) {
    List<TemplateVariable> variables = new ArrayList<>();
    for (String line : template.lines().toList()) {
      if (line.isBlank() || line.strip().startsWith("#")) {
        continue;
      }
      Matcher secret = SECRET_MARKER.matcher(line);
      boolean isSecret = secret.find();
      Matcher matcher = LINE.matcher(isSecret ? line.substring(0, secret.start()) : line);
      if (!matcher.matches()) {
        continue;
      }
      String key = matcher.group(1).toUpperCase(Locale.ROOT);
      if (!KEY.matcher(key).matches() || key.startsWith(EnvironmentVariable.RESERVED_PREFIX)) {
        continue;
      }
      variables.add(new TemplateVariable(key, unquote(matcher.group(2)), isSecret));
    }
    return variables;
  }

  private static String unquote(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }
}
