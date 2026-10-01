package com.cloudflow.project.service;

import java.util.UUID;

/** Published after a project is deleted, so other modules can clean up external state. */
public record ProjectDeletedEvent(UUID projectId) {}
