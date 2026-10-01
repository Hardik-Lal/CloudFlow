export interface FieldError {
  field: string;
  message: string;
}

/** RFC 9457 Problem Details body returned by the CloudFlow API. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: FieldError[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }

  get fieldErrors(): FieldError[] {
    return this.problem.errors ?? [];
  }
}

/** Human-readable message for any error thrown by the API layer. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 429) {
    return "Too many requests. Please wait a moment and try again.";
  }
  if (error instanceof ApiError) {
    const fields = error.fieldErrors.map((e) => `${e.field} ${e.message}`).join(", ");
    return fields ? `${error.message}: ${fields}` : error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Something went wrong";
}
