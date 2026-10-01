package com.cloudflow.user.service;

import com.cloudflow.common.crypto.EncryptionService;
import com.cloudflow.common.exception.UnauthorizedException;
import com.cloudflow.user.domain.UserGithubCredential;
import com.cloudflow.user.repository.UserGithubCredentialRepository;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores and retrieves users' GitHub OAuth tokens, encrypted at rest. */
@Service
public class GithubCredentialService {

  private final UserGithubCredentialRepository repository;
  private final EncryptionService encryptionService;

  public GithubCredentialService(
      UserGithubCredentialRepository repository, EncryptionService encryptionService) {
    this.repository = repository;
    this.encryptionService = encryptionService;
  }

  @Transactional
  public void storeToken(UUID userId, String accessToken, Collection<String> scopes) {
    String encrypted = encryptionService.encrypt(accessToken);
    String scopeList = String.join(",", scopes);
    repository
        .findById(userId)
        .ifPresentOrElse(
            credential -> credential.replaceToken(encrypted, scopeList),
            () -> repository.save(new UserGithubCredential(userId, encrypted, scopeList)));
  }

  /**
   * Returns the decrypted GitHub token.
   *
   * @throws UnauthorizedException if the user has no stored token and must sign in again
   */
  @Transactional(readOnly = true)
  public String getAccessToken(UUID userId) {
    return repository
        .findById(userId)
        .map(credential -> encryptionService.decrypt(credential.getAccessTokenEncrypted()))
        .orElseThrow(
            () -> new UnauthorizedException("GitHub is not connected; sign in with GitHub again"));
  }
}
