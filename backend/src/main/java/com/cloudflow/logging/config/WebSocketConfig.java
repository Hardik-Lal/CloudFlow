package com.cloudflow.logging.config;

import com.cloudflow.common.config.WebProperties;
import com.cloudflow.logging.security.StompAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over native WebSockets at {@code /ws}. Clients authenticate in the CONNECT frame; every
 * SUBSCRIBE is authorized against CloudFlow's RBAC. The in-memory broker is enough for a single
 * backend instance; running several instances needs an external broker relay.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  public static final String ENDPOINT = "/ws";

  private final WebProperties webProperties;
  private final StompAuthorizationInterceptor authorizationInterceptor;

  public WebSocketConfig(
      WebProperties webProperties, StompAuthorizationInterceptor authorizationInterceptor) {
    this.webProperties = webProperties;
    this.authorizationInterceptor = authorizationInterceptor;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint(ENDPOINT)
        .setAllowedOrigins(webProperties.corsAllowedOrigins().toArray(String[]::new));
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authorizationInterceptor);
  }
}
