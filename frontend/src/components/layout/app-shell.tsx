import Link from "next/link";
import type { ReactNode } from "react";
import { OrganizationSwitcher } from "./organization-switcher";
import { UserMenu } from "./user-menu";

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-full flex-1 flex-col">
      <header className="border-b">
        <div className="mx-auto flex h-14 w-full max-w-6xl items-center gap-4 px-6">
          <Link href="/organizations" className="font-semibold tracking-tight">
            CloudFlow
          </Link>
          <OrganizationSwitcher />
          <nav className="flex items-center gap-4 text-sm text-muted-foreground">
            <Link href="/organizations" className="hover:text-foreground">
              Organizations
            </Link>
          </nav>
          <div className="ml-auto">
            <UserMenu />
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">{children}</main>
    </div>
  );
}
