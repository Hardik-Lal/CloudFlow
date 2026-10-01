package com.cloudflow.user.service;

import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.user.domain.User;
import com.cloudflow.user.dto.GithubUserProfile;
import com.cloudflow.user.dto.UserResponse;
import com.cloudflow.user.dto.UserSummary;
import com.cloudflow.user.repository.UserRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final Clock clock;

  public UserService(UserRepository userRepository, Clock clock) {
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /** Creates the user on first sign-in, or refreshes their profile, and records the login. */
  @Transactional
  public UserResponse recordGithubLogin(GithubUserProfile profile) {
    User user =
        userRepository
            .findByGithubId(profile.githubId())
            .orElseGet(() -> new User(profile.githubId(), profile.login()));
    user.updateProfile(profile.login(), profile.email(), profile.name(), profile.avatarUrl());
    user.recordLogin(clock.instant());
    return UserResponse.from(userRepository.save(user));
  }

  @Transactional(readOnly = true)
  public UserResponse getUser(UUID userId) {
    return userRepository
        .findById(userId)
        .map(UserResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
  }

  @Transactional(readOnly = true)
  public Optional<UserSummary> findByUsername(String username) {
    return userRepository.findByUsernameIgnoreCase(username).map(UserSummary::from);
  }

  @Transactional(readOnly = true)
  public Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds) {
    return userRepository.findAllById(userIds).stream()
        .map(UserSummary::from)
        .collect(Collectors.toMap(UserSummary::id, Function.identity()));
  }
}
