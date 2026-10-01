package com.cloudflow.github.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class GithubClientConfig {

  static final String API_VERSION = "2022-11-28";

  @Bean
  RestClient githubRestClient(RestClient.Builder builder, GithubProperties properties) {
    HttpClientSettings settings =
        HttpClientSettings.defaults()
            .withConnectTimeout(properties.timeout())
            .withReadTimeout(properties.timeout())
            // Tarball downloads redirect to codeload.github.com.
            .withRedirects(HttpRedirects.FOLLOW);
    return builder
        .baseUrl(properties.apiBaseUrl())
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .defaultHeader("X-GitHub-Api-Version", API_VERSION)
        .build();
  }
}
