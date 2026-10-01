package com.cloudflow.user.repository;

import com.cloudflow.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByGithubId(long githubId);

  @Query("select u from User u where lower(u.username) = lower(:username)")
  Optional<User> findByUsernameIgnoreCase(String username);
}
