package com.cloudflow.cicd.dto;

import java.util.List;
import java.util.UUID;

/**
 * The workflow CloudFlow would commit, shown for approval before anything is written.
 *
 * @param fileExists whether committing will replace an existing workflow file
 */
public record PipelinePreviewResponse(
    UUID environmentId,
    String workflowPath,
    String branch,
    String content,
    boolean fileExists,
    List<RequiredSecret> secrets) {}
