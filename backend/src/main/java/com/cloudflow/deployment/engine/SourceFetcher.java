package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.config.DeploymentProperties;
import com.cloudflow.github.client.GithubModels.Commit;
import com.cloudflow.github.service.GithubService;
import com.cloudflow.project.dto.ProjectSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Retrieves a repository at a commit from GitHub as a tarball and extracts it. */
@Component
public class SourceFetcher {

  private static final int MAX_COMMIT_MESSAGE = 500;

  private final GithubService githubService;
  private final DeploymentProperties properties;

  public SourceFetcher(GithubService githubService, DeploymentProperties properties) {
    this.githubService = githubService;
    this.properties = properties;
  }

  /**
   * @param ref branch name or commit SHA
   * @param userId user whose GitHub token is used
   */
  public SourceCheckout fetch(UUID userId, ProjectSnapshot project, String ref, Path workDirectory)
      throws IOException {
    Commit commit =
        githubService.resolveCommit(
            userId, project.repositoryOwner(), project.repositoryName(), ref);
    Path tarball = workDirectory.resolve("source.tar.gz");
    githubService.downloadSource(
        userId, project.repositoryOwner(), project.repositoryName(), commit.sha(), tarball);

    Path sourceDirectory = workDirectory.resolve("src");
    TarballExtractor.extract(
        tarball, sourceDirectory, properties.maxSourceSizeMb() * 1024L * 1024L);
    Files.delete(tarball);

    return new SourceCheckout(
        sourceDirectory, commit.sha(), subjectLine(commit), rootFileNames(sourceDirectory));
  }

  private static Set<String> rootFileNames(Path directory) throws IOException {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
    }
  }

  private static String subjectLine(Commit commit) {
    if (commit.commit() == null || commit.commit().message() == null) {
      return null;
    }
    String subject = commit.commit().message().lines().findFirst().orElse("");
    return subject.length() > MAX_COMMIT_MESSAGE
        ? subject.substring(0, MAX_COMMIT_MESSAGE)
        : subject;
  }
}
