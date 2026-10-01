package com.cloudflow.user.repository;

import com.cloudflow.user.domain.UserGithubCredential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGithubCredentialRepository extends JpaRepository<UserGithubCredential, UUID> {}
