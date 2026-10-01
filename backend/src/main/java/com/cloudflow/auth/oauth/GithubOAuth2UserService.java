package com.cloudflow.auth.oauth;

import com.cloudflow.auth.service.GithubLoginService;
import com.cloudflow.user.dto.GithubUserProfile;
import com.cloudflow.user.dto.UserResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Loads the GitHub profile after the OAuth code exchange, provisions the CloudFlow user, and stores
 * the GitHub access token for later GitHub API calls.
 */
@Component
public class GithubOAuth2UserService extends DefaultOAuth2UserService {

  /** Attribute added to the OAuth2 principal carrying the CloudFlow user id. */
  public static final String CLOUDFLOW_USER_ID = "cloudflow_user_id";

  private static final String GITHUB_ID = "id";

  private final GithubLoginService githubLoginService;

  public GithubOAuth2UserService(GithubLoginService githubLoginService) {
    this.githubLoginService = githubLoginService;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User githubUser = super.loadUser(userRequest);
    OAuth2AccessToken accessToken = userRequest.getAccessToken();

    UserResponse user =
        githubLoginService.completeLogin(
            toProfile(githubUser.getAttributes()),
            accessToken.getTokenValue(),
            accessToken.getScopes());

    Map<String, Object> attributes = new HashMap<>(githubUser.getAttributes());
    attributes.put(CLOUDFLOW_USER_ID, user.id().toString());
    return new DefaultOAuth2User(githubUser.getAuthorities(), attributes, GITHUB_ID);
  }

  static GithubUserProfile toProfile(Map<String, Object> attributes) {
    if (!(attributes.get(GITHUB_ID) instanceof Number githubId)
        || !(attributes.get("login") instanceof String login)) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("invalid_user_info", "GitHub profile is missing id or login", null));
    }
    return new GithubUserProfile(
        githubId.longValue(),
        login,
        stringAttribute(attributes, "name"),
        stringAttribute(attributes, "email"),
        stringAttribute(attributes, "avatar_url"));
  }

  private static String stringAttribute(Map<String, Object> attributes, String name) {
    return attributes.get(name) instanceof String value && !value.isBlank() ? value : null;
  }
}
