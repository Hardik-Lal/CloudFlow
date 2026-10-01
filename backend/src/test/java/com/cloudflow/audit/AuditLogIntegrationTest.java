package com.cloudflow.audit;

import static com.cloudflow.support.TestApi.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestProjects;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AuditLogIntegrationTest {

  private static final String SECRET = "super-secret-value-123";

  @Autowired private TestApi api;
  @Autowired private TestUsers users;
  @Autowired private TestProjects projects;

  @Test
  void recordsConfigurationAndMembershipChangesWithoutSecretValues() throws Exception {
    TestUser owner = users.create("owner");
    String orgId = api.createOrganization(owner);
    TestUser developer = api.addMember(owner, orgId, "DEVELOPER");
    String projectId = projects.create(owner, orgId, List.of("package.json"));
    String envId =
        read(
            api.post(
                owner, "/api/v1/projects/" + projectId + "/environments", "{\"type\":\"STAGING\"}"),
            "$.id");
    api.put(
        developer,
        "/api/v1/environments/" + envId + "/variables/API_KEY",
        "{\"value\":\"" + SECRET + "\",\"secret\":true}");
    api.patch(
        owner,
        "/api/v1/organizations/" + orgId + "/members/" + developer.id(),
        "{\"role\":\"VIEWER\"}");

    MvcTestResult audit = api.get(owner, "/api/v1/organizations/" + orgId + "/audit-logs");

    assertThat(audit).hasStatusOk();
    assertThat(audit)
        .bodyJson()
        .extractingPath("$.content[*].action")
        .asArray()
        .containsExactly(
            "MEMBER_ROLE_CHANGED",
            "VARIABLE_SET",
            "ENVIRONMENT_CREATED",
            "PROJECT_CREATED",
            "MEMBER_ADDED",
            "ORGANIZATION_CREATED");
    assertThat(audit)
        .bodyJson()
        .extractingPath("$.content[1].actorUsername")
        .isEqualTo(developer.username());
    assertThat(audit).bodyJson().extractingPath("$.content[1].details.secret").isEqualTo("true");
    assertThat(audit).bodyJson().extractingPath("$.content[0].details.to").isEqualTo("VIEWER");
    assertThat(audit.getResponse().getContentAsString()).doesNotContain(SECRET);

    assertThat(api.get(owner, "/api/v1/organizations/" + orgId + "/audit-logs?action=VARIABLE_SET"))
        .bodyJson()
        .extractingPath("$.totalElements")
        .isEqualTo(1);
  }

  @Test
  void onlyOwnersAndAdminsCanReadTheAuditLog() {
    TestUser owner = users.create("owner");
    String orgId = api.createOrganization(owner);
    TestUser admin = api.addMember(owner, orgId, "ADMIN");
    TestUser developer = api.addMember(owner, orgId, "DEVELOPER");

    assertThat(api.get(admin, "/api/v1/organizations/" + orgId + "/audit-logs")).hasStatusOk();
    assertThat(api.get(developer, "/api/v1/organizations/" + orgId + "/audit-logs"))
        .hasStatus(HttpStatus.FORBIDDEN);
  }
}
