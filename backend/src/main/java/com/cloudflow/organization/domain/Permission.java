package com.cloudflow.organization.domain;

/**
 * Fine-grained, organization-scoped permissions. Services check permissions, never role names; the
 * role-to-permission mapping lives only in {@link Role}. See docs/requirements.md §4.
 */
public enum Permission {
  ORG_READ,
  ORG_UPDATE,
  ORG_DELETE,
  MEMBER_READ,
  MEMBER_MANAGE,
  PROJECT_READ,
  PROJECT_WRITE,
  PROJECT_DELETE,
  ENV_READ,
  ENV_WRITE,
  ENV_WRITE_PRODUCTION,
  DEPLOYMENT_READ,
  DEPLOYMENT_TRIGGER,
  DEPLOYMENT_TRIGGER_PRODUCTION,
  PIPELINE_READ,
  PIPELINE_WRITE,
  AI_USE,
  AI_APPLY,
  AUDIT_READ
}
