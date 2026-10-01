import type { NextConfig } from "next";

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const websocketUrl = apiBaseUrl.replace(/^http/, "ws");
const isDevelopment = process.env.NODE_ENV === "development";

/**
 * The UI talks only to its own origin and the CloudFlow API (HTTP + WebSocket). Next.js injects
 * inline bootstrap scripts, hence 'unsafe-inline'; development additionally needs 'unsafe-eval'.
 */
const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDevelopment ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: https://avatars.githubusercontent.com",
  "font-src 'self'",
  `connect-src 'self' ${apiBaseUrl} ${websocketUrl}`,
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
].join("; ");

const nextConfig: NextConfig = {
  // Produces a self-contained server bundle used by the production Docker image.
  output: "standalone",
  poweredByHeader: false,
  // Pin the workspace root so a lockfile elsewhere on the machine is never picked up.
  turbopack: { root: __dirname },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: contentSecurityPolicy },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default nextConfig;
