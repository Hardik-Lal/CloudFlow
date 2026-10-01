package com.cloudflow.github.client;

import java.util.List;

/** One page of a paginated GitHub listing; {@code hasNext} comes from the {@code Link} header. */
public record GithubPage<T>(List<T> items, int page, int perPage, boolean hasNext) {}
