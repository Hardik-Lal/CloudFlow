package com.cloudflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point of the CloudFlow platform backend (modular monolith). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CloudFlowApplication {

  public static void main(String[] args) {
    SpringApplication.run(CloudFlowApplication.class, args);
  }
}
