package com.cloudflow.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** Guards the RBAC matrix documented in docs/requirements.md §4. */
class RoleTest {

  @Test
  void ownerHasEveryPermission() {
    assertThat(Role.OWNER.permissions()).containsExactlyInAnyOrder(Permission.values());
  }

  @Test
  void adminHasEverythingExceptDeletingTheOrganization() {
    assertThat(Role.ADMIN.permissions())
        .isEqualTo(EnumSet.complementOf(EnumSet.of(Permission.ORG_DELETE)));
  }

  @Test
  void developerCannotManageMembersProductionOrAudit() {
    assertThat(Role.DEVELOPER.has(Permission.PROJECT_WRITE)).isTrue();
    assertThat(Role.DEVELOPER.has(Permission.DEPLOYMENT_TRIGGER)).isTrue();
    assertThat(Role.DEVELOPER.has(Permission.AI_APPLY)).isTrue();
    assertThat(Role.DEVELOPER.has(Permission.MEMBER_MANAGE)).isFalse();
    assertThat(Role.DEVELOPER.has(Permission.ENV_WRITE_PRODUCTION)).isFalse();
    assertThat(Role.DEVELOPER.has(Permission.DEPLOYMENT_TRIGGER_PRODUCTION)).isFalse();
    assertThat(Role.DEVELOPER.has(Permission.PROJECT_DELETE)).isFalse();
    assertThat(Role.DEVELOPER.has(Permission.AUDIT_READ)).isFalse();
  }

  @Test
  void viewerIsReadOnlyPlusAssistant() {
    assertThat(Role.VIEWER.permissions())
        .containsExactlyInAnyOrder(
            Permission.ORG_READ,
            Permission.MEMBER_READ,
            Permission.PROJECT_READ,
            Permission.ENV_READ,
            Permission.DEPLOYMENT_READ,
            Permission.PIPELINE_READ,
            Permission.AI_USE);
  }

  @Test
  void ownerCanManageAnyone() {
    for (Role current : Role.values()) {
      for (Role target : Role.values()) {
        assertThat(Role.OWNER.canManage(current, target)).isTrue();
      }
    }
  }

  @Test
  void adminManagesOnlyDevelopersAndViewersAndNeverGrantsOwner() {
    assertThat(Role.ADMIN.canManage(null, Role.ADMIN)).isTrue();
    assertThat(Role.ADMIN.canManage(Role.VIEWER, Role.DEVELOPER)).isTrue();
    assertThat(Role.ADMIN.canManage(Role.DEVELOPER, null)).isTrue();
    assertThat(Role.ADMIN.canManage(null, Role.OWNER)).isFalse();
    assertThat(Role.ADMIN.canManage(Role.ADMIN, Role.VIEWER)).isFalse();
    assertThat(Role.ADMIN.canManage(Role.OWNER, null)).isFalse();
  }

  @Test
  void developersAndViewersCannotManageMembers() {
    assertThat(Role.DEVELOPER.canManage(null, Role.VIEWER)).isFalse();
    assertThat(Role.VIEWER.canManage(null, Role.VIEWER)).isFalse();
  }
}
