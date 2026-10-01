package com.cloudflow.logging.dto;

import java.time.Instant;
import java.util.UUID;

/** One line of a running container's output (secrets masked). */
public record RuntimeLogLine(UUID deploymentId, String message, Instant receivedAt) {}
