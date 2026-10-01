package com.cloudflow.logging.security;

import com.cloudflow.auth.security.AuthenticatedUserConverter;
import com.cloudflow.common.security.AuthenticatedUser;
import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions with the same JWT access tokens as the REST API (sent in the CONNECT
 * frame's {@code Authorization} header, because browsers cannot set headers on WebSocket
 * handshakes) and authorizes every subscription. Clients never send messages.
 */
@Component
public class StompAuthorizationInterceptor implements ChannelInterceptor {

  private static final String BEARER = "Bearer ";

  private final JwtDecoder jwtDecoder;
  private final AuthenticatedUserConverter userConverter;
  private final TopicAuthorizer topicAuthorizer;

  public StompAuthorizationInterceptor(
      JwtDecoder jwtDecoder,
      AuthenticatedUserConverter userConverter,
      TopicAuthorizer topicAuthorizer) {
    this.jwtDecoder = jwtDecoder;
    this.userConverter = userConverter;
    this.topicAuthorizer = topicAuthorizer;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }
    switch (accessor.getCommand()) {
      case CONNECT ->
          accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
      case SUBSCRIBE -> topicAuthorizer.authorize(accessor.getDestination(), userOf(accessor).id());
      case SEND -> throw new AccessDeniedException("Clients cannot send messages");
      default -> {
        // UNSUBSCRIBE, DISCONNECT, heartbeats: nothing to check.
      }
    }
    return message;
  }

  private AbstractAuthenticationToken authenticate(String authorization) {
    if (authorization == null || !authorization.startsWith(BEARER)) {
      throw new AccessDeniedException("Missing access token");
    }
    try {
      return userConverter.convert(jwtDecoder.decode(authorization.substring(BEARER.length())));
    } catch (JwtException e) {
      throw new AccessDeniedException("Invalid access token");
    }
  }

  private static AuthenticatedUser userOf(StompHeaderAccessor accessor) {
    Principal principal = accessor.getUser();
    if (principal instanceof AbstractAuthenticationToken token
        && token.getPrincipal() instanceof AuthenticatedUser user) {
      return user;
    }
    throw new AccessDeniedException("Not authenticated");
  }
}
