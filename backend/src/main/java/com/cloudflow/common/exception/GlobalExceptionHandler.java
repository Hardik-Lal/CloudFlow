package com.cloudflow.common.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 Problem Details. Security exceptions raised by the filter
 * chain are routed here too (see {@code SecurityProblemHandler}), so every error shares one format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  ProblemDetail handleNotFound(ResourceNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Resource not found", ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  ProblemDetail handleConflict(ConflictException ex) {
    return problem(HttpStatus.CONFLICT, ProblemTypes.CONFLICT, "Conflict", ex.getMessage());
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
    return validationProblem(List.of(fieldError(ex.getField(), ex.getMessage())));
  }

  @ExceptionHandler(UnprocessableException.class)
  ProblemDetail handleUnprocessable(UnprocessableException ex) {
    ProblemDetail problem =
        problem(
            HttpStatus.UNPROCESSABLE_CONTENT,
            ProblemTypes.UNPROCESSABLE,
            "Request cannot be processed",
            ex.getMessage());
    problem.setProperty(
        "errors",
        ex.getErrors().stream().map(issue -> fieldError(issue.field(), issue.message())).toList());
    return problem;
  }

  /** Last line of defence for unique-constraint races that slip past service-level checks. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
    log.debug("Data integrity violation", ex);
    return problem(
        HttpStatus.CONFLICT,
        ProblemTypes.CONFLICT,
        "Conflict",
        "The request conflicts with existing data");
  }

  @ExceptionHandler(ExternalServiceException.class)
  ProblemDetail handleExternalService(ExternalServiceException ex) {
    log.warn("{} call failed: {}", ex.getService(), ex.getMessage(), ex.getCause());
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_GATEWAY,
            ProblemTypes.UPSTREAM,
            ex.getService() + " error",
            ex.getMessage());
    problem.setProperty("service", ex.getService());
    return problem;
  }

  @ExceptionHandler({UnauthorizedException.class, AuthenticationException.class})
  ProblemDetail handleUnauthorized(RuntimeException ex) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        ProblemTypes.UNAUTHORIZED,
        "Unauthorized",
        "Authentication is required or the provided credentials are invalid");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return problem(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Forbidden", ex.getMessage());
  }

  /**
   * The HTTP firewall validates some parts of a request lazily (e.g. header values when a
   * controller reads them), so rejections can surface here rather than in the filter chain.
   */
  @ExceptionHandler(RequestRejectedException.class)
  ProblemDetail handleRejectedRequest(RequestRejectedException ex) {
    return problem(
        HttpStatus.BAD_REQUEST,
        ProblemTypes.VALIDATION,
        "Invalid request",
        "The request contains characters that are not allowed");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ProblemTypes.INTERNAL,
        "Internal server error",
        "An unexpected error occurred");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
            .toList();
    return ResponseEntity.badRequest().body(validationProblem(errors));
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                fieldError(
                                    result.getMethodParameter().getParameterName(),
                                    error.getDefaultMessage())))
            .toList();
    return ResponseEntity.badRequest().body(validationProblem(errors));
  }

  private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            ProblemTypes.VALIDATION,
            "Validation failed",
            "Request contains invalid fields");
    problem.setProperty("errors", errors);
    return problem;
  }

  private static Map<String, String> fieldError(@Nullable String field, @Nullable String message) {
    return Map.of(
        "field", field == null ? "" : field, "message", message == null ? "is invalid" : message);
  }

  private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(type);
    problem.setTitle(title);
    return problem;
  }
}
