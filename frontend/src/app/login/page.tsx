import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginCard } from "@/components/auth/login-card";

export const metadata: Metadata = { title: "Sign in" };

export default function LoginPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-6 py-16">
      {/* LoginCard reads the query string, which is only known in the browser. */}
      <Suspense>
        <LoginCard />
      </Suspense>
    </main>
  );
}
