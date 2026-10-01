package com.cloudflow.organization.service;

import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.organization.domain.Organization;
import com.cloudflow.organization.repository.OrganizationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal, unauthenticated organization lookups for other modules. */
@Service
public class OrganizationLookupService {

  private final OrganizationRepository organizationRepository;

  public OrganizationLookupService(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  @Transactional(readOnly = true)
  public String slug(UUID organizationId) {
    return organizationRepository
        .findById(organizationId)
        .map(Organization::getSlug)
        .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
  }
}
