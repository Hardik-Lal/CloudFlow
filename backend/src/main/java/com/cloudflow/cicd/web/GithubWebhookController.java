package com.cloudflow.cicd.web;

import com.cloudflow.cicd.service.GithubWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** GitHub webhook receiver; authenticated by the HMAC signature, not a user session. */
@RestController
public class GithubWebhookController {

  private final GithubWebhookService webhookService;

  public GithubWebhookController(GithubWebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @PostMapping("/api/v1/webhooks/github")
  public ResponseEntity<Void> receive(
      @RequestHeader(name = "X-GitHub-Event", required = false) String event,
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
      @RequestBody byte[] payload) {
    // The raw body is needed as-is to verify the signature.
    boolean tracked = webhookService.handle(event, payload, signature);
    return tracked ? ResponseEntity.noContent().build() : ResponseEntity.accepted().build();
  }
}
