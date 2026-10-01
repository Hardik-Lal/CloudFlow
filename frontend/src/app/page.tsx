import Link from "next/link";
import { GithubSignInButton } from "@/components/auth/github-sign-in-button";
import { buttonVariants } from "@/components/ui/button";

const capabilities = [
  "Projects from GitHub repositories",
  "Development, Staging, and Production environments",
  "Docker-based deployments with rollback",
  "GitHub Actions CI/CD",
  "Live logs and health monitoring",
  "AI-assisted troubleshooting",
];

export default function HomePage() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col justify-center gap-8 px-6 py-16">
      <div className="space-y-3">
        <h1 className="text-4xl font-semibold tracking-tight">CloudFlow</h1>
        <p className="text-lg text-muted-foreground">
          An AI-assisted Internal Developer Platform that unifies project management, environment
          configuration, containerized deployment, CI/CD, monitoring, and logging.
        </p>
      </div>
      <div className="flex flex-wrap gap-3">
        <GithubSignInButton />
        <Link href="/organizations" className={buttonVariants({ variant: "outline", size: "lg" })}>
          Open dashboard
        </Link>
      </div>
      <ul className="grid gap-2 sm:grid-cols-2">
        {capabilities.map((capability) => (
          <li key={capability} className="rounded-lg border px-4 py-3 text-sm">
            {capability}
          </li>
        ))}
      </ul>
    </main>
  );
}
