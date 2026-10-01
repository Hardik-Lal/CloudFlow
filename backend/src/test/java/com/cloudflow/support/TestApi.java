package com.cloudflow.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Authenticated MockMvc calls and common fixtures (organizations, members) for API tests. */
@TestComponent
public class TestApi {

  private final MockMvcTester mvc;
  private final TestUsers users;

  public TestApi(MockMvcTester mvc, TestUsers users) {
    this.mvc = mvc;
    this.users = users;
  }

  public MvcTestResult get(TestUser user, String uri) {
    return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, user.authorization()).exchange();
  }

  public MvcTestResult delete(TestUser user, String uri) {
    return mvc.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, user.authorization()).exchange();
  }

  public MvcTestResult post(TestUser user, String uri, String json) {
    return mvc.post()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, user.authorization())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }

  public MvcTestResult put(TestUser user, String uri, String json) {
    return mvc.put()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, user.authorization())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }

  public MvcTestResult patch(TestUser user, String uri, String json) {
    return mvc.patch()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, user.authorization())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }

  /** Creates an organization owned by {@code owner} and returns its id. */
  public String createOrganization(TestUser owner) {
    MvcTestResult result =
        post(owner, "/api/v1/organizations", "{\"name\":\"Org " + UUID.randomUUID() + "\"}");
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return read(result, "$.id");
  }

  /** Creates a new user and adds them to the organization with the given role. */
  public TestUser addMember(TestUser owner, String organizationId, String role) {
    TestUser user = users.create(role.toLowerCase());
    assertThat(
            post(
                owner,
                "/api/v1/organizations/" + organizationId + "/members",
                "{\"username\":\"" + user.username() + "\",\"role\":\"" + role + "\"}"))
        .hasStatus(HttpStatus.CREATED);
    return user;
  }

  public static String read(MvcTestResult result, String jsonPath) {
    try {
      return JsonPath.read(result.getResponse().getContentAsString(), jsonPath).toString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }
}
