package com.cloudflow.github.dto;

public record BranchResponse(String name, String headCommitSha, boolean isProtected) {}
