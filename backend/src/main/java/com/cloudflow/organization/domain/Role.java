package com.cloudflow.organization.domain;

import static com.cloudflow.organization.domain.Permission.AI_APPLY;
import static com.cloudflow.organization.domain.Permission.AI_USE;
import static com.cloudflow.organization.domain.Permission.DEPLOYMENT_READ;
import static com.cloudflow.organization.domain.Permission.DEPLOYMENT_TRIGGER;
import static com.cloudflow.organization.domain.Permission.ENV_READ;
import static com.cloudflow.organization.domain.Permission.ENV_WRITE;
import static com.cloudflow.organization.domain.Permission.MEMBER_READ;
import static com.cloudflow.organization.domain.Permission.ORG_DELETE;
import static com.cloudflow.organization.domain.Permission.ORG_READ;
import static com.cloudflow.organization.domain.Permission.PIPELINE_READ;
import static com.cloudflow.organization.domain.Permission.PIPELINE_WRITE;
import static com.cloudflow.organization.domain.Permission.PROJECT_READ;
import static com.cloudflow.organization.domain.Permission.PROJECT_WRITE;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Organization roles, ordered from most to least privileged. */
public enum Role {
  OWNER(EnumSet.allOf(Permission.class)),
  ADMIN(EnumSet.complementOf(EnumSet.of(ORG_DELETE))),
  DEVELOPER(
      EnumSet.of(
          ORG_READ,
          MEMBER_READ,
          PROJECT_READ,
          PROJECT_WRITE,
          ENV_READ,
          ENV_WRITE,
          DEPLOYMENT_READ,
          DEPLOYMENT_TRIGGER,
          PIPELINE_READ,
          PIPELINE_WRITE,
          AI_USE,
          AI_APPLY)),
  VIEWER(
      EnumSet.of(
          ORG_READ, MEMBER_READ, PROJECT_READ, ENV_READ, DEPLOYMENT_READ, PIPELINE_READ, AI_USE));

  private final Set<Permission> permissions;

  Role(Set<Permission> permissions) {
    this.permissions = Collections.unmodifiableSet(permissions);
  }

  public boolean has(Permission permission) {
    return permissions.contains(permission);
  }

  public Set<Permission> permissions() {
    return permissions;
  }

  /**
   * Whether a member with this role may assign {@code newRole} to a member currently holding {@code
   * currentRole} ({@code null} when adding a new member). Owners manage everyone; Admins manage
   * only Developers and Viewers and can never grant Owner.
   */
  public boolean canManage(Role currentRole, Role newRole) {
    if (this == OWNER) {
      return true;
    }
    if (!has(Permission.MEMBER_MANAGE) || newRole == OWNER) {
      return false;
    }
    return currentRole == null || currentRole == DEVELOPER || currentRole == VIEWER;
  }
}
