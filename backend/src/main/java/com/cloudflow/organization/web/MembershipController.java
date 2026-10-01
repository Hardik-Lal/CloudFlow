package com.cloudflow.organization.web;

import com.cloudflow.common.security.AuthenticatedUser;
import com.cloudflow.organization.dto.AddMemberRequest;
import com.cloudflow.organization.dto.MemberResponse;
import com.cloudflow.organization.dto.UpdateMemberRoleRequest;
import com.cloudflow.organization.service.MembershipService;
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
@RequestMapping("/api/v1/organizations/{organizationId}/members")
public class MembershipController {

  private final MembershipService membershipService;

  public MembershipController(MembershipService membershipService) {
    this.membershipService = membershipService;
  }

  @GetMapping
  public List<MemberResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID organizationId) {
    return membershipService.list(organizationId, user.id());
  }

  @PostMapping
  public ResponseEntity<MemberResponse> add(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID organizationId,
      @Valid @RequestBody AddMemberRequest request) {
    MemberResponse member = membershipService.add(organizationId, user.id(), request);
    return ResponseEntity.created(
            URI.create("/api/v1/organizations/" + organizationId + "/members/" + member.userId()))
        .body(member);
  }

  @PatchMapping("/{userId}")
  public MemberResponse changeRole(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID organizationId,
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateMemberRoleRequest request) {
    return membershipService.changeRole(organizationId, user.id(), userId, request);
  }

  @DeleteMapping("/{userId}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID organizationId,
      @PathVariable UUID userId) {
    membershipService.remove(organizationId, user.id(), userId);
    return ResponseEntity.noContent().build();
  }
}
