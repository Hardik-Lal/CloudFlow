package com.cloudflow.assistant.domain;

/** Suggestions are never applied automatically: PENDING until a user applies or rejects them. */
public enum SuggestionStatus {
  PENDING,
  APPLIED,
  REJECTED
}
