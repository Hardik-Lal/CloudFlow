package com.cloudflow.deployment.engine;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Extracts a GitHub source tarball, removing the top-level {@code owner-repo-sha/} directory.
 *
 * <p>Repository contents are untrusted, so entries that would land outside the target directory
 * (absolute paths, {@code ..}, links pointing outside) are rejected, and the total extracted size
 * is capped.
 */
public final class TarballExtractor {

  private TarballExtractor() {}

  public static void extract(Path tarball, Path targetDirectory, long maxBytes) throws IOException {
    Path root = Files.createDirectories(targetDirectory).toRealPath();
    long written = 0;
    try (InputStream file = Files.newInputStream(tarball);
        InputStream gzip = new GzipCompressorInputStream(new BufferedInputStream(file));
        TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        String relative = stripTopLevelDirectory(entry.getName());
        if (relative.isEmpty() || entry.isGlobalPaxHeader() || entry.isPaxHeader()) {
          continue;
        }
        Path destination = resolveInside(root, relative);
        if (entry.isDirectory()) {
          Files.createDirectories(destination);
        } else if (entry.isSymbolicLink()) {
          Path linkTarget = destination.getParent().resolve(entry.getLinkName()).normalize();
          if (!linkTarget.startsWith(root)) {
            throw new IOException("Symbolic link escapes the repository: " + relative);
          }
          Files.createDirectories(destination.getParent());
          Files.createSymbolicLink(destination, Path.of(entry.getLinkName()));
        } else if (entry.isFile()) {
          written += entry.getSize();
          if (written > maxBytes) {
            throw new IOException(
                "Repository exceeds the maximum source size of "
                    + maxBytes / (1024 * 1024)
                    + " MB");
          }
          Files.createDirectories(destination.getParent());
          try (OutputStream out = Files.newOutputStream(destination)) {
            tar.transferTo(out);
          }
          if ((entry.getMode() & 0100) != 0) {
            makeExecutable(destination);
          }
        }
        // Hard links, devices, and FIFOs are skipped: they are never needed for a build.
      }
    }
  }

  static String stripTopLevelDirectory(String name) {
    int slash = name.indexOf('/');
    return slash < 0 ? "" : name.substring(slash + 1);
  }

  static Path resolveInside(Path root, String relative) throws IOException {
    Path destination = root.resolve(relative).normalize();
    if (!destination.startsWith(root) || destination.equals(root)) {
      throw new IOException("Archive entry escapes the repository: " + relative);
    }
    return destination;
  }

  private static void makeExecutable(Path file) throws IOException {
    try {
      Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(file));
      permissions.add(PosixFilePermission.OWNER_EXECUTE);
      permissions.add(PosixFilePermission.GROUP_EXECUTE);
      permissions.add(PosixFilePermission.OTHERS_EXECUTE);
      Files.setPosixFilePermissions(file, permissions);
    } catch (UnsupportedOperationException e) {
      // Non-POSIX file system: executable bits cannot be represented.
    }
  }
}
