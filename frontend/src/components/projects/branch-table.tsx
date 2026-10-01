"use client";

import { ShieldIcon } from "lucide-react";
import { QueryState } from "@/components/query-state";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useBranches } from "@/hooks/use-projects";
import type { Project } from "@/lib/api/types";

export function BranchTable({ project }: { project: Project }) {
  const { data: branches, isPending, error } = useBranches(project.id);

  return (
    <QueryState isPending={isPending} error={error} errorTitle="Could not load branches">
      {() => (
        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Branch</TableHead>
                <TableHead>Head commit</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {branches?.map((branch) => (
                <TableRow key={branch.name}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{branch.name}</span>
                      {branch.name === project.repository.defaultBranch && (
                        <Badge variant="secondary">default</Badge>
                      )}
                      {branch.isProtected && (
                        <ShieldIcon
                          className="size-3.5 text-muted-foreground"
                          aria-label="Protected"
                        />
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <code className="text-xs">{branch.headCommitSha.slice(0, 7)}</code>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </QueryState>
  );
}
