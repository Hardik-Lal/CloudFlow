package com.cloudflow.deployment.engine;

import java.nio.file.Path;
import java.util.Set;

/** Repository contents at a resolved commit, extracted for building. */
public record SourceCheckout(
    Path directory, String commitSha, String commitMessage, Set<String> rootFileNames) {}
