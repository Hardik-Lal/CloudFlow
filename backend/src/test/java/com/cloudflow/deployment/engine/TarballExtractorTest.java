package com.cloudflow.deployment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudflow.support.Tarballs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarballExtractorTest {

  @TempDir Path temp;

  @Test
  void extractsFilesWithoutTheTopLevelDirectoryAndKeepsExecutableBits() throws IOException {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("pom.xml", "<project/>");
    files.put("src/", "");
    files.put("src/App.java", "class App {}");
    files.put("mvnw.sh", "#!/bin/sh");
    Path tarball = write(Tarballs.githubArchive("octo-demo-abc1234", files));

    Path target = temp.resolve("src");
    TarballExtractor.extract(tarball, target, 1_000_000);

    assertThat(target.resolve("pom.xml")).hasContent("<project/>");
    assertThat(target.resolve("src/App.java")).hasContent("class App {}");
    assertThat(Files.isExecutable(target.resolve("mvnw.sh"))).isTrue();
    assertThat(target.resolve("octo-demo-abc1234")).doesNotExist();
  }

  @Test
  void rejectsEntriesThatEscapeTheTargetDirectory() throws IOException {
    Path tarball = write(Tarballs.rawArchive("top/../../evil.txt", "pwned"));

    assertThatThrownBy(() -> TarballExtractor.extract(tarball, temp.resolve("src"), 1_000_000))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("escapes");
    assertThat(temp.resolve("evil.txt")).doesNotExist();
  }

  @Test
  void enforcesTheSizeLimit() throws IOException {
    Path tarball = write(Tarballs.githubArchive("top", Map.of("big.bin", "x".repeat(2_000))));

    assertThatThrownBy(() -> TarballExtractor.extract(tarball, temp.resolve("src"), 1_000))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("maximum source size");
  }

  @Test
  void stripsOnlyTheFirstPathComponent() {
    assertThat(TarballExtractor.stripTopLevelDirectory("owner-repo-sha/a/b.txt"))
        .isEqualTo("a/b.txt");
    assertThat(TarballExtractor.stripTopLevelDirectory("owner-repo-sha/")).isEmpty();
    assertThat(TarballExtractor.stripTopLevelDirectory("pax_global_header")).isEmpty();
  }

  private Path write(byte[] bytes) throws IOException {
    return Files.write(temp.resolve("source.tar.gz"), bytes);
  }
}
