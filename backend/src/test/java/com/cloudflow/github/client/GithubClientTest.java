package com.cloudflow.github.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cloudflow.common.exception.ExternalServiceException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubClientTest {

  private static final String REPO_JSON =
      """
      {"id": 42, "name": "demo", "full_name": "octo/demo", "owner": {"login": "octo"},
       "html_url": "https://github.com/octo/demo", "clone_url": "https://github.com/octo/demo.git",
       "default_branch": "main", "private": true, "language": "Java",
       "pushed_at": "2026-09-01T10:00:00Z", "unexpected_field": 1}
      """;

  private MockRestServiceServer server;
  private GithubClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new GithubClient(builder.build());
  }

  @Test
  void mapsRepositoryAndSendsUserToken() {
    server
        .expect(requestTo("https://api.github.test/repos/octo/demo"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
        .andRespond(withSuccess(REPO_JSON, MediaType.APPLICATION_JSON));

    GithubModels.Repository repository = client.getRepository("user-token", "octo", "demo");

    assertThat(repository.fullName()).isEqualTo("octo/demo");
    assertThat(repository.owner().login()).isEqualTo("octo");
    assertThat(repository.isPrivate()).isTrue();
    assertThat(repository.defaultBranch()).isEqualTo("main");
    server.verify();
  }

  @Test
  void reportsNextPageFromLinkHeader() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(
        HttpHeaders.LINK,
        "<https://api.github.test/user/repos?page=2>; rel=\"next\", "
            + "<https://api.github.test/user/repos?page=5>; rel=\"last\"");
    server
        .expect(
            requestTo(
                "https://api.github.test/user/repos?sort=updated"
                    + "&affiliation=owner,collaborator,organization_member&page=1&per_page=30"))
        .andRespond(
            withSuccess("[" + REPO_JSON + "]", MediaType.APPLICATION_JSON).headers(headers));

    GithubPage<GithubModels.Repository> page = client.listRepositories("t", 1, 30);

    assertThat(page.items()).hasSize(1);
    assertThat(page.hasNext()).isTrue();
  }

  @Test
  void followsBranchPaginationUpToTheLimit() {
    HttpHeaders next = new HttpHeaders();
    next.add(HttpHeaders.LINK, "<https://api.github.test/next>; rel=\"next\"");
    for (int page = 1; page <= GithubClient.MAX_BRANCH_PAGES; page++) {
      server
          .expect(
              requestTo(
                  "https://api.github.test/repos/octo/demo/branches?per_page=100&page=" + page))
          .andRespond(
              withSuccess(
                      "[{\"name\": \"b"
                          + page
                          + "\", \"commit\": {\"sha\": \"abc\"}, "
                          + "\"protected\": false}]",
                      MediaType.APPLICATION_JSON)
                  .headers(next));
    }

    List<GithubModels.Branch> branches = client.listBranches("t", "octo", "demo");

    assertThat(branches).extracting(GithubModels.Branch::name).containsExactly("b1", "b2", "b3");
    server.verify();
  }

  @Test
  void translatesNotFoundToResourceNotFound() {
    server
        .expect(requestTo("https://api.github.test/repos/octo/secret"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> client.getRepository("t", "octo", "secret"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void translatesAuthFailuresToExternalServiceErrors() {
    server
        .expect(requestTo("https://api.github.test/repos/octo/demo"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> client.getRepository("revoked", "octo", "demo"))
        .isInstanceOf(ExternalServiceException.class)
        .hasMessageContaining("sign in with GitHub again");
  }

  @Test
  void followsTarballRedirectWithoutSendingTheToken(@TempDir Path directory) throws Exception {
    String download = "https://codeload.github.test/octo/demo/legacy.tar.gz/abc123?token=signed";
    byte[] tarball = {0x1f, (byte) 0x8b, 1, 2, 3};
    server
        .expect(requestTo("https://api.github.test/repos/octo/demo/tarball/abc123"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
        .andRespond(withStatus(HttpStatus.FOUND).location(URI.create(download)));
    server
        .expect(requestTo(download))
        .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
        .andRespond(withSuccess(tarball, MediaType.APPLICATION_OCTET_STREAM));
    Path target = directory.resolve("source.tar.gz");

    client.downloadTarball("user-token", "octo", "demo", "abc123", target);

    assertThat(Files.readAllBytes(target)).isEqualTo(tarball);
    server.verify();
  }
}
