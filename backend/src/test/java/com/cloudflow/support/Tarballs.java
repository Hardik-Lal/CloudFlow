package com.cloudflow.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/** Builds gzipped tarballs shaped like GitHub's source archives for tests. */
public final class Tarballs {

  private Tarballs() {}

  /**
   * @param files relative path to content; paths ending in {@code /} are directories
   */
  public static byte[] githubArchive(String topLevelDirectory, Map<String, String> files) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(bytes);
        TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      tar.putArchiveEntry(new TarArchiveEntry(topLevelDirectory + "/"));
      tar.closeArchiveEntry();
      for (Map.Entry<String, String> file : files.entrySet()) {
        String name = topLevelDirectory + "/" + file.getKey();
        TarArchiveEntry entry = new TarArchiveEntry(name);
        if (!name.endsWith("/")) {
          byte[] content = file.getValue().getBytes(StandardCharsets.UTF_8);
          entry.setSize(content.length);
          entry.setMode(file.getKey().endsWith(".sh") ? 0100755 : 0100644);
          tar.putArchiveEntry(entry);
          tar.write(content);
        } else {
          tar.putArchiveEntry(entry);
        }
        tar.closeArchiveEntry();
      }
      tar.finish();
      gzip.finish();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** An archive containing a single raw entry name (for path-traversal tests). */
  public static byte[] rawArchive(String entryName, String content) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(bytes);
        TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
      byte[] data = content.getBytes(StandardCharsets.UTF_8);
      TarArchiveEntry entry = new TarArchiveEntry(entryName, true);
      entry.setSize(data.length);
      tar.putArchiveEntry(entry);
      tar.write(data);
      tar.closeArchiveEntry();
      tar.finish();
      gzip.finish();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
