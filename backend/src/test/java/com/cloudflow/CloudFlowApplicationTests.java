package com.cloudflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class CloudFlowApplicationTests {

  @Autowired private MockMvcTester mockMvc;

  @Test
  void healthEndpointIsPublicAndReportsUp() {
    assertThat(mockMvc.get().uri("/actuator/health"))
        .hasStatus(HttpStatus.OK)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
  }
}
