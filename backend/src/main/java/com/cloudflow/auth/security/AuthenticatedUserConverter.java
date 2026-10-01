package com.cloudflow.auth.security;

import com.cloudflow.common.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a validated access token into an authentication whose principal is an {@link
 * AuthenticatedUser}. No authorities are attached: permissions are organization-scoped and checked
 * by the organization module.
 */
@Component
public class AuthenticatedUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    AuthenticatedUser user =
        new AuthenticatedUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("username"));
    return UsernamePasswordAuthenticationToken.authenticated(user, jwt, List.of());
  }
}
