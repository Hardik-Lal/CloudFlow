package com.cloudflow.assistant.web;

import com.cloudflow.assistant.client.AiModels.Answer;
import com.cloudflow.assistant.dto.AssistantQueryRequest;
import com.cloudflow.assistant.dto.GenerateSuggestionRequest;
import com.cloudflow.assistant.dto.KnowledgeStatusResponse;
import com.cloudflow.assistant.dto.ReindexResponse;
import com.cloudflow.assistant.dto.SuggestionResponse;
import com.cloudflow.assistant.service.AssistantService;
import com.cloudflow.assistant.service.KnowledgeService;
import com.cloudflow.assistant.service.SuggestionService;
import com.cloudflow.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AssistantController {

  private final AssistantService assistantService;
  private final KnowledgeService knowledgeService;
  private final SuggestionService suggestionService;

  public AssistantController(
      AssistantService assistantService,
      KnowledgeService knowledgeService,
      SuggestionService suggestionService) {
    this.assistantService = assistantService;
    this.knowledgeService = knowledgeService;
    this.suggestionService = suggestionService;
  }

  @PostMapping("/projects/{projectId}/assistant/query")
  public Answer ask(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody AssistantQueryRequest request) {
    return assistantService.ask(projectId, user.id(), request.question(), request.environmentId());
  }

  @PostMapping("/deployments/{deploymentId}/analysis")
  public Answer analyze(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID deploymentId) {
    return assistantService.analyzeDeployment(deploymentId, user.id());
  }

  @GetMapping("/projects/{projectId}/knowledge")
  public KnowledgeStatusResponse knowledge(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
    return knowledgeService.status(projectId, user.id());
  }

  @PostMapping("/projects/{projectId}/knowledge/reindex")
  public ReindexResponse reindex(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
    return knowledgeService.reindex(projectId, user.id());
  }

  @GetMapping("/projects/{projectId}/suggestions")
  public List<SuggestionResponse> suggestions(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
    return suggestionService.list(projectId, user.id());
  }

  @PostMapping("/projects/{projectId}/suggestions")
  public ResponseEntity<SuggestionResponse> generate(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody GenerateSuggestionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(suggestionService.generate(projectId, user.id(), request));
  }

  @PostMapping("/suggestions/{suggestionId}/apply")
  public SuggestionResponse apply(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID suggestionId) {
    return suggestionService.apply(suggestionId, user.id());
  }

  @PostMapping("/suggestions/{suggestionId}/reject")
  public SuggestionResponse reject(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID suggestionId) {
    return suggestionService.reject(suggestionId, user.id());
  }
}
