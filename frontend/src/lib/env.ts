/**
 * Public runtime configuration. Only NEXT_PUBLIC_* variables are exposed to the browser.
 */
export const env = {
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
} as const;
