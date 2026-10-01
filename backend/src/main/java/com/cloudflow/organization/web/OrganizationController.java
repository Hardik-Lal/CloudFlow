package com.cloudflow.organization.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.organization.dto.CreateOrganizationRequest;
import com.cloudflow.organization.dto.OrganizationResponse;
import com.cloudflow.organization.dto.UpdateOrganizationRequest;
import com.cloudflow.organization.service.OrganizationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

  private final OrganizationService organizationService;

  public OrganizationController(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @GetMapping
  public List<OrganizationResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
    return organizationService.listForUser(user.id());
  }

  @PostMapping
  public ResponseEntity<OrganizationResponse> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @Valid @RequestBody CreateOrganizationRequest request) {
    OrganizationResponse organization = organizationService.create(user.id(), request);
    return ResponseEntity.created(URI.create("/api/v1/organizations/" + organization.id()))
        .body(organization);
  }

  @GetMapping("/{organizationId}")
  public OrganizationResponse get(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID organizationId) {
    return organizationService.get(organizationId, user.id());
  }

  @PatchMapping("/{organizationId}")
  public OrganizationResponse update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID organizationId,
      @Valid @RequestBody UpdateOrganizationRequest request) {
    return organizationService.update(organizationId, user.id(), request);
  }

  @DeleteMapping("/{organizationId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID organizationId) {
    organizationService.delete(organizationId, user.id());
    return ResponseEntity.noContent().build();
  }
}
