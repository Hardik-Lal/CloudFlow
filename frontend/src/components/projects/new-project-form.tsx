"use client";

import { LockIcon, SearchIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { FormField } from "@/components/form-field";
import { QueryState } from "@/components/query-state";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useCreateProject, useGithubRepositories } from "@/hooks/use-projects";
import { ApiError, errorMessage } from "@/lib/api/errors";
import type { GithubRepository } from "@/lib/api/types";
import { formatRelativeTime } from "@/lib/format";
import { cn } from "@/lib/utils";

export function NewProjectForm({ organizationId }: { organizationId: string }) {
  const [selected, setSelected] = useState<GithubRepository | null>(null);

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <RepositoryPicker selected={selected} onSelect={setSelected} />
      {selected ? (
        <ProjectDetailsForm
          key={selected.id}
          organizationId={organizationId}
          repository={selected}
        />
      ) : (
        <div className="flex items-center justify-center rounded-xl border border-dashed p-10 text-sm text-muted-foreground">
          Select a repository to continue.
        </div>
      )}
    </div>
  );
}

interface RepositoryPickerProps {
  selected: GithubRepository | null;
  onSelect: (repository: GithubRepository) => void;
}

function RepositoryPicker({ selected, onSelect }: RepositoryPickerProps) {
  const [filter, setFilter] = useState("");
  const { data, isPending, error, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useGithubRepositories();

  const repositories = useMemo(() => {
    const all = data?.pages.flatMap((page) => page.items) ?? [];
    const term = filter.trim().toLowerCase();
    return term ? all.filter((repo) => repo.fullName.toLowerCase().includes(term)) : all;
  }, [data, filter]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>1. Choose a repository</CardTitle>
        <CardDescription>Repositories your GitHub account can access.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        <div className="relative">
          <SearchIcon className="pointer-events-none absolute top-2 left-2.5 size-4 text-muted-foreground" />
          <Input
            aria-label="Filter repositories"
            placeholder="Filter loaded repositories"
            className="pl-8"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
          />
        </div>
        <QueryState
          isPending={isPending}
          error={error}
          errorTitle="Could not load GitHub repositories"
          skeletonClassName="h-64"
        >
          {() => (
            <ul className="max-h-96 divide-y overflow-y-auto rounded-lg border" role="listbox">
              {repositories.map((repository) => (
                <li key={repository.id}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={selected?.id === repository.id}
                    onClick={() => onSelect(repository)}
                    className={cn(
                      "flex w-full items-start justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-muted",
                      selected?.id === repository.id && "bg-muted",
                    )}
                  >
                    <span className="min-w-0">
                      <span className="flex items-center gap-1.5 font-medium">
                        {repository.isPrivate && <LockIcon className="size-3" />}
                        <span className="truncate">{repository.fullName}</span>
                      </span>
                      {repository.description && (
                        <span className="line-clamp-1 text-xs text-muted-foreground">
                          {repository.description}
                        </span>
                      )}
                    </span>
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {repository.language ?? ""} · {formatRelativeTime(repository.pushedAt)}
                    </span>
                  </button>
                </li>
              ))}
              {repositories.length === 0 && (
                <li className="px-3 py-6 text-center text-sm text-muted-foreground">
                  No repositories match.
                </li>
              )}
            </ul>
          )}
        </QueryState>
        {hasNextPage && (
          <Button variant="outline" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
            {isFetchingNextPage ? "Loading…" : "Load more repositories"}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

interface ProjectDetailsFormProps {
  organizationId: string;
  repository: GithubRepository;
}

function ProjectDetailsForm({ organizationId, repository }: ProjectDetailsFormProps) {
  const [name, setName] = useState(repository.name);
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState(repository.description ?? "");
  const createProject = useCreateProject(organizationId);
  const router = useRouter();

  const fieldError = (field: string) =>
    createProject.error instanceof ApiError
      ? createProject.error.fieldErrors.find((e) => e.field === field)?.message
      : undefined;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    createProject.mutate(
      {
        repositoryFullName: repository.fullName,
        name: name.trim(),
        slug: slug.trim() || undefined,
        description: description.trim() || undefined,
      },
      {
        onSuccess: (project) => {
          toast.success(`Project ${project.name} created`);
          router.push(`/projects/${project.id}`);
        },
      },
    );
  }

  return (
    <Card>
      <form onSubmit={handleSubmit}>
        <CardHeader>
          <CardTitle>2. Project details</CardTitle>
          <CardDescription>
            Linked to <span className="font-medium text-foreground">{repository.fullName}</span>{" "}
            (default branch {repository.defaultBranch}).
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 py-4">
          <FormField id="project-name" label="Name" error={fieldError("name")}>
            <Input
              id="project-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={100}
              required
            />
          </FormField>
          <FormField
            id="project-slug"
            label="Slug (optional)"
            hint="Used in image names and URLs. Derived from the name if empty."
            error={fieldError("slug")}
          >
            <Input
              id="project-slug"
              value={slug}
              onChange={(event) => setSlug(event.target.value)}
              pattern="[a-z0-9]+(-[a-z0-9]+)*"
              minLength={3}
              maxLength={50}
            />
          </FormField>
          <FormField id="project-description" label="Description" error={fieldError("description")}>
            <Input
              id="project-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={500}
            />
          </FormField>
          {createProject.isError && !fieldError("name") && !fieldError("slug") && (
            <p className="text-sm text-destructive">{errorMessage(createProject.error)}</p>
          )}
        </CardContent>
        <CardFooter>
          <Button type="submit" disabled={createProject.isPending || !name.trim()}>
            {createProject.isPending ? "Creating…" : "Create project"}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
