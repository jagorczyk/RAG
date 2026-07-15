import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The backend is run locally during development and can be restarted while
  // Next.js stays up. Do not reuse a socket opened by the previous JVM.
  httpAgentOptions: {
    keepAlive: false,
  },
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.BACKEND_URL ?? process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080"}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
