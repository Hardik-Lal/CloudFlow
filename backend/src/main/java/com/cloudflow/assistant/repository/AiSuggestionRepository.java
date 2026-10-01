package com.cloudflow.assistant.repository;

import com.cloudflow.assistant.domain.AiSuggestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, UUID> {

  List<AiSuggestion> findTop50ByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
