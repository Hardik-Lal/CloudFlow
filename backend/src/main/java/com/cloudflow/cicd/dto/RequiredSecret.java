package com.cloudflow.cicd.dto;

/**
 * A repository secret the generated workflow needs.
 *
 * @param value the value to use when CloudFlow knows it, otherwise {@code null}
 */
public record RequiredSecret(String name, String description, String value) {}
