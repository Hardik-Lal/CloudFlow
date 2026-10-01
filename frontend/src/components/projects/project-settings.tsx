"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { FormField } from "@/components/form-field";
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
import { useDeleteProject, useUpdateProject } from "@/hooks/use-projects";
import { errorMessage } from "@/lib/api/errors";
import type { Project } from "@/lib/api/types";

export function ProjectSettings({ project }: { project: Project }) {
  const canWrite = project.permissions.includes("PROJECT_WRITE");
  const canDelete = project.permissions.includes("PROJECT_DELETE");

  return (
    <div className="grid max-w-2xl gap-6">
      <DetailsCard project={project} disabled={!canWrite} />
      {canDelete && <DeleteCard project={project} />}
    </div>
  );
}

function DetailsCard({ project, disabled }: { project: Project; disabled: boolean }) {
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? "");
  const update = useUpdateProject(project.id);
  const unchanged = name === project.name && description === (project.description ?? "");

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    update.mutate(
      { name: name.trim(), description: description.trim() || null },
      {
        onSuccess: () => toast.success("Project updated"),
        onError: (error) => toast.error(errorMessage(error)),
      },
    );
  }

  return (
    <Card>
      <form onSubmit={handleSubmit}>
        <CardHeader>
          <CardTitle>General</CardTitle>
          <CardDescription>The slug ({project.slug}) cannot be changed.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 py-4">
          <FormField id="project-settings-name" label="Name">
            <Input
              id="project-settings-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={100}
              disabled={disabled}
              required
            />
          </FormField>
          <FormField id="project-settings-description" label="Description">
            <Input
              id="project-settings-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={500}
              disabled={disabled}
            />
          </FormField>
        </CardContent>
        <CardFooter>
          <Button
            type="submit"
            disabled={disabled || update.isPending || !name.trim() || unchanged}
          >
            Save
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}

function DeleteCard({ project }: { project: Project }) {
  const deleteProject = useDeleteProject(project.id, project.organizationId);
  const router = useRouter();

  function handleDelete() {
    deleteProject.mutate(undefined, {
      onSuccess: () => {
        toast.success(`${project.name} deleted`);
        router.replace(`/organizations/${project.organizationId}`);
      },
      onError: (error) => toast.error(errorMessage(error)),
    });
  }

  return (
    <Card className="border-destructive/40">
      <CardHeader>
        <CardTitle>Delete project</CardTitle>
        <CardDescription>
          Removes the project from CloudFlow. The GitHub repository is not affected.
        </CardDescription>
      </CardHeader>
      <CardFooter>
        <ConfirmDialog
          trigger={
            <Button variant="destructive" disabled={deleteProject.isPending}>
              Delete project
            </Button>
          }
          title={`Delete ${project.name}?`}
          description="This cannot be undone."
          confirmLabel="Delete"
          onConfirm={handleDelete}
        />
      </CardFooter>
    </Card>
  );
}
