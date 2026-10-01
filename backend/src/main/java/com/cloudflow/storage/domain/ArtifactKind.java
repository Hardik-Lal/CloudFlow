package com.cloudflow.storage.domain;

/** What an artifact is. */
public enum ArtifactKind {
  /** Outputs of an image build: the Dockerfile used and the vulnerability report. */
  BUILD_ARTIFACT,
  /** Files CloudFlow generated and committed with the user's approval (workflows, AI changes). */
  GENERATED_FILE,
  /** The complete log of a finished deployment. */
  ARCHIVED_LOG
}
