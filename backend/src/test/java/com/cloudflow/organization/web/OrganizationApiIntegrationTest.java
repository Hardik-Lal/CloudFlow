package com.cloudflow.organization.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class OrganizationApiIntegrationTest {

  @Autowired private TestApi api;
  @Autowired private TestUsers users;

  private TestUser owner;
  private String orgId;

  @BeforeEach
  void createOrganization() throws Exception {
    owner = users.create("owner");
    MvcTestResult created =
        post(owner, "/api/v1/organizations", "{\"name\":\"Org " + UUID.randomUUID() + "\"}");
    assertThat(created).hasStatus(HttpStatus.CREATED);
    orgId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
  }

  @Test
  void creatorBecomesOwnerAndSeesOrganization() {
    assertThat(get(owner, "/api/v1/organizations/" + orgId))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.role")
        .isEqualTo("OWNER");
    assertThat(get(owner, "/api/v1/organizations"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].id")
        .asArray()
        .contains(orgId);
  }

  @Test
  void createDerivesSlugAndRejectsDuplicates() {
    String name = "Acme " + UUID.randomUUID().toString().substring(0, 8);
    String expectedSlug = name.toLowerCase().replace(' ', '-');

    MvcTestResult first = post(owner, "/api/v1/organizations", "{\"name\":\"" + name + "\"}");
    assertThat(first)
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .extractingPath("$.slug")
        .isEqualTo(expectedSlug);
    assertThat(first.getResponse().getHeader(HttpHeaders.LOCATION))
        .startsWith("/api/v1/organizations/");

    assertThat(post(owner, "/api/v1/organizations", "{\"name\":\"" + name + "\"}"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:cloudflow:problem:conflict");
  }

  @Test
  void createValidatesInput() {
    assertThat(post(owner, "/api/v1/organizations", "{\"name\":\"\",\"slug\":\"Bad Slug\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .contains("name", "slug");
  }

  @Test
  void nonMembersCannotSeeTheOrganization() {
    TestUser outsider = users.create("outsider");

    assertThat(get(outsider, "/api/v1/organizations/" + orgId)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(outsider, "/api/v1/organizations/" + orgId + "/members"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(outsider, "/api/v1/organizations"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].id")
        .asArray()
        .doesNotContain(orgId);
  }

  @Test
  void viewersCanReadButNotModify() {
    TestUser viewer = addMember("viewer", "VIEWER");

    assertThat(get(viewer, "/api/v1/organizations/" + orgId)).hasStatusOk();
    assertThat(get(viewer, "/api/v1/organizations/" + orgId + "/members")).hasStatusOk();
    assertThat(patch(viewer, "/api/v1/organizations/" + orgId, "{\"name\":\"Hijacked\"}"))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:cloudflow:problem:forbidden");
  }

  @Test
  void onlyOwnersCanDeleteOrganizations() {
    TestUser admin = addMember("admin", "ADMIN");

    assertThat(delete(admin, "/api/v1/organizations/" + orgId)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(delete(owner, "/api/v1/organizations/" + orgId)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(get(owner, "/api/v1/organizations/" + orgId)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void adminsCanRenameOrganizations() {
    TestUser admin = addMember("admin", "ADMIN");

    assertThat(patch(admin, "/api/v1/organizations/" + orgId, "{\"name\":\"Renamed\"}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.name")
        .isEqualTo("Renamed");
  }

  @Test
  void addMemberRequiresExistingUserAndRejectsDuplicates() {
    assertThat(
            post(
                owner,
                membersPath(),
                "{\"username\":\"nobody-" + UUID.randomUUID() + "\",\"role\":\"VIEWER\"}"))
        .hasStatus(HttpStatus.NOT_FOUND);

    TestUser developer = addMember("dev", "DEVELOPER");
    assertThat(
            post(
                owner,
                membersPath(),
                "{\"username\":\"" + developer.username() + "\",\"role\":\"VIEWER\"}"))
        .hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void adminsCannotGrantOwnerOrManageOtherAdmins() {
    TestUser admin = addMember("admin", "ADMIN");
    TestUser otherAdmin = addMember("admin2", "ADMIN");
    TestUser newcomer = users.create("newcomer");

    assertThat(
            post(
                admin,
                membersPath(),
                "{\"username\":\"" + newcomer.username() + "\",\"role\":\"OWNER\"}"))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(patch(admin, memberPath(otherAdmin), "{\"role\":\"VIEWER\"}"))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(delete(admin, memberPath(owner))).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void developersCannotManageMembers() {
    TestUser developer = addMember("dev", "DEVELOPER");
    TestUser newcomer = users.create("newcomer");

    assertThat(
            post(
                developer,
                membersPath(),
                "{\"username\":\"" + newcomer.username() + "\",\"role\":\"VIEWER\"}"))
        .hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void roleChangesTakeEffectImmediately() {
    TestUser member = addMember("promoted", "VIEWER");
    assertThat(patch(member, "/api/v1/organizations/" + orgId, "{\"name\":\"X\"}"))
        .hasStatus(HttpStatus.FORBIDDEN);

    assertThat(patch(owner, memberPath(member), "{\"role\":\"ADMIN\"}"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.role")
        .isEqualTo("ADMIN");
    assertThat(patch(member, "/api/v1/organizations/" + orgId, "{\"name\":\"X\"}")).hasStatusOk();
  }

  @Test
  void lastOwnerCannotLeaveOrBeDemoted() {
    assertThat(delete(owner, memberPath(owner))).hasStatus(HttpStatus.CONFLICT);
    assertThat(patch(owner, memberPath(owner), "{\"role\":\"ADMIN\"}"))
        .hasStatus(HttpStatus.CONFLICT);

    TestUser secondOwner = addMember("owner2", "OWNER");
    assertThat(delete(owner, memberPath(owner))).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(get(secondOwner, membersPath()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].userId")
        .asArray()
        .containsExactly(secondOwner.id().toString());
  }

  @Test
  void anyMemberCanLeave() {
    TestUser viewer = addMember("leaver", "VIEWER");

    assertThat(delete(viewer, memberPath(viewer))).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(get(viewer, "/api/v1/organizations/" + orgId)).hasStatus(HttpStatus.NOT_FOUND);
  }

  private TestUser addMember(String prefix, String role) {
    TestUser user = users.create(prefix);
    assertThat(
            post(
                owner,
                membersPath(),
                "{\"username\":\"" + user.username() + "\",\"role\":\"" + role + "\"}"))
        .hasStatus(HttpStatus.CREATED);
    return user;
  }

  private String membersPath() {
    return "/api/v1/organizations/" + orgId + "/members";
  }

  private String memberPath(TestUser member) {
    return membersPath() + "/" + member.id();
  }

  private MvcTestResult get(TestUser user, String uri) {
    return api.get(user, uri);
  }

  private MvcTestResult delete(TestUser user, String uri) {
    return api.delete(user, uri);
  }

  private MvcTestResult post(TestUser user, String uri, String json) {
    return api.post(user, uri, json);
  }

  private MvcTestResult patch(TestUser user, String uri, String json) {
    return api.patch(user, uri, json);
  }
}
