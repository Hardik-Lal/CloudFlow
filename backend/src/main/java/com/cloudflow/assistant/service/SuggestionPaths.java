package com.cloudflow.assistant.service;

import com.cloudflow.assistant.domain.SuggestionType;
import java.util.regex.Pattern;

/**
 * Where approved suggestions are written. The model may propose a path, but only paths matching the
 * type's safe pattern are accepted; anything else falls back to the default. CloudFlow's own
 * pipeline files ({@code cloudflow-*.yml}) can never be overwritten this way.
 */
final class SuggestionPaths {

  private static final Pattern DOCKERFILE =
      Pattern.compile("^([A-Za-z0-9_-]+/)?[A-Za-z0-9._-]*[Dd]ockerfile[A-Za-z0-9._-]*$");
  private static final Pattern WORKFLOW =
      Pattern.compile("^\\.github/workflows/(?!cloudflow-)[A-Za-z0-9._-]+\\.ya?ml$");
  private static final Pattern DOCUMENTATION = Pattern.compile("^(docs/)?[A-Za-z0-9._-]+\\.md$");

  private SuggestionPaths() {}

  static String resolve(SuggestionType type, String proposed) {
    String candidate = proposed == null ? "" : proposed.strip();
    return switch (type) {
      case DOCKERFILE -> accept(candidate, DOCKERFILE, "Dockerfile");
      case WORKFLOW -> accept(candidate, WORKFLOW, ".github/workflows/ci.yml");
      case DOCUMENTATION -> accept(candidate, DOCUMENTATION, "docs/CLOUDFLOW.md");
      case ENV_TEMPLATE -> "variables";
    };
  }

  private static String accept(String candidate, Pattern pattern, String fallback) {
    return !candidate.contains("..") && pattern.matcher(candidate).matches() ? candidate : fallback;
  }
}
