package com.cloudflow.github.dto;

import java.util.List;

public record GithubRepositoryPage(
    List<GithubRepositoryResponse> items, int page, int perPage, boolean hasNext) {}
