package com.cloudflow.deployment.engine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** HTTP health probes. A 2xx or 3xx response means the application is serving. */
@Component
public class HealthChecker {

  private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(PROBE_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public HealthProbe probe(URI url) {
    long start = System.nanoTime();
    try {
      HttpResponse<Void> response =
          httpClient.send(
              HttpRequest.newBuilder(url).timeout(PROBE_TIMEOUT).GET().build(),
              HttpResponse.BodyHandlers.discarding());
      int status = response.statusCode();
      return new HealthProbe(status >= 200 && status < 400, status, elapsedMs(start), null);
    } catch (IOException e) {
      return new HealthProbe(
          false, null, elapsedMs(start), e.getClass().getSimpleName() + ": " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new HealthProbe(false, null, elapsedMs(start), "Interrupted");
    }
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
