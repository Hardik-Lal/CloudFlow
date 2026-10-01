package com.cloudflow.organization.dto;

import com.cloudflow.organization.domain.OrganizationMembership;
import com.cloudflow.organization.domain.Role;
import com.cloudflow.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
    UUID userId,
    String username,
    String displayName,
    String avatarUrl,
    Role role,
    Instant joinedAt) {

  public static MemberResponse from(OrganizationMembership membership, UserSummary user) {
    return new MemberResponse(
        membership.getUserId(),
        user.username(),
        user.displayName(),
        user.avatarUrl(),
        membership.getRole(),
        membership.getCreatedAt());
  }
}
