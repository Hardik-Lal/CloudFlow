package com.cloudflow.project.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The GitHub repository linked to a project (table {@code repositories}). */
@Entity
@Table(name = "repositories")
public class SourceRepository extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false, updatable = false)
  private Project project;

  @Column(nullable = false)
  private long githubRepoId;

  @Column(nullable = false, length = 100)
  private String owner;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 201)
  private String fullName;

  @Column(nullable = false, length = 1024)
  private String htmlUrl;

  @Column(nullable = false, length = 1024)
  private String cloneUrl;

  @Column(nullable = false)
  private String defaultBranch;

  @Column(name = "private", nullable = false)
  private boolean isPrivate;

  @Column(length = 100)
  private String language;

  @Column(nullable = false)
  private Instant lastSyncedAt;

  @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("name")
  private List<Branch> branches = new ArrayList<>();

  protected SourceRepository() {}

  SourceRepository(Project project, RepositoryMetadata metadata, Instant syncedAt) {
    this.project = project;
    apply(metadata, syncedAt);
  }

  /** Replaces metadata with the latest values from GitHub (repositories can be renamed). */
  public void apply(RepositoryMetadata metadata, Instant syncedAt) {
    this.githubRepoId = metadata.githubRepoId();
    this.owner = metadata.owner();
    this.name = metadata.name();
    this.fullName = metadata.fullName();
    this.htmlUrl = metadata.htmlUrl();
    this.cloneUrl = metadata.cloneUrl();
    this.defaultBranch = metadata.defaultBranch();
    this.isPrivate = metadata.isPrivate();
    this.language = metadata.language();
    this.lastSyncedAt = syncedAt;
  }

  /** Makes the stored branches match GitHub: updates existing, adds new, removes deleted. */
  public void syncBranches(Collection<BranchMetadata> latest) {
    Map<String, BranchMetadata> byName =
        latest.stream().collect(Collectors.toMap(BranchMetadata::name, Function.identity()));
    branches.removeIf(branch -> !byName.containsKey(branch.getName()));
    Map<String, Branch> existing =
        branches.stream().collect(Collectors.toMap(Branch::getName, Function.identity()));
    for (BranchMetadata metadata : latest) {
      Branch branch = existing.get(metadata.name());
      if (branch == null) {
        branches.add(
            new Branch(this, metadata.name(), metadata.headCommitSha(), metadata.isProtected()));
      } else {
        branch.update(metadata.headCommitSha(), metadata.isProtected());
      }
    }
  }

  public long getGithubRepoId() {
    return githubRepoId;
  }

  public String getOwner() {
    return owner;
  }

  public String getName() {
    return name;
  }

  public String getFullName() {
    return fullName;
  }

  public String getHtmlUrl() {
    return htmlUrl;
  }

  public String getCloneUrl() {
    return cloneUrl;
  }

  public String getDefaultBranch() {
    return defaultBranch;
  }

  public boolean isPrivate() {
    return isPrivate;
  }

  public String getLanguage() {
    return language;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }

  public List<Branch> getBranches() {
    return Collections.unmodifiableList(branches);
  }
}
