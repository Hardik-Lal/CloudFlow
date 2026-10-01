package com.cloudflow.auth.service;

import com.cloudflow.auth.config.AuthProperties;
import com.cloudflow.auth.dto.AccessToken;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived access tokens. Tokens carry identity only; organization roles are resolved on
 * each request so that role changes take effect immediately.
 */
@Service
public class AccessTokenService {

  static final String USERNAME_CLAIM = "username";

  private final JwtEncoder jwtEncoder;
  private final AuthProperties properties;
  private final Clock clock;

  public AccessTokenService(JwtEncoder jwtEncoder, AuthProperties properties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  public AccessToken issue(UUID userId, String username) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.jwtIssuer())
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTokenTtl()))
            .claim(USERNAME_CLAIM, username)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new AccessToken(token, properties.accessTokenTtl().toSeconds());
  }
}
