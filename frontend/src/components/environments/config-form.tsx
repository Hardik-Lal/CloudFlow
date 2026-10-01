"use client";

import { useState, type FormEvent } from "react";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  useConfigTemplates,
  useDeploymentConfig,
  useDeploymentTargets,
  useUpdateDeploymentConfig,
} from "@/hooks/use-environments";
import { ApiError, errorMessage } from "@/lib/api/errors";
import type {
  AppType,
  ConfigTemplate,
  ConfigTemplateInfo,
  DeploymentConfig,
  DeploymentConfigInput,
  DeploymentTarget,
  DeploymentTargetInfo,
  Environment,
} from "@/lib/api/types";
import { ValidationPanel } from "./validation-panel";

interface ConfigFormProps {
  environment: Environment;
  appType: AppType;
  canWrite: boolean;
}

export function ConfigForm({ environment, appType, canWrite }: ConfigFormProps) {
  const config = useDeploymentConfig(environment.id);
  const templates = useConfigTemplates(appType);
  const targets = useDeploymentTargets();

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="lg:col-span-2">
        <QueryState
          isPending={config.isPending || templates.isPending || targets.isPending}
          error={config.error ?? templates.error ?? targets.error}
          errorTitle="Could not load configuration"
          skeletonClassName="h-96"
        >
          {() =>
            config.data &&
            templates.data &&
            targets.data && (
              <ConfigEditor
                key={environment.id}
                environment={environment}
                initial={config.data}
                templates={templates.data}
                targets={targets.data}
                canWrite={canWrite}
              />
            )
          }
        </QueryState>
      </div>
      <ValidationPanel environmentId={environment.id} />
    </div>
  );
}

interface ConfigEditorProps {
  environment: Environment;
  initial: DeploymentConfig;
  templates: ConfigTemplateInfo[];
  targets: DeploymentTargetInfo[];
  canWrite: boolean;
}

function ConfigEditor({ environment, initial, templates, targets, canWrite }: ConfigEditorProps) {
  const [form, setForm] = useState<DeploymentConfigInput>(() => toInput(initial));
  const update = useUpdateDeploymentConfig(environment.id, environment.projectId);
  const isDocker = form.template === "DOCKER";

  const set = <K extends keyof DeploymentConfigInput>(key: K, value: DeploymentConfigInput[K]) =>
    setForm((current) => ({ ...current, [key]: value }));

  const fieldError = (field: string) =>
    update.error instanceof ApiError
      ? update.error.fieldErrors.find((e) => e.field === field)?.message
      : undefined;

  function applyTemplateDefaults(template: ConfigTemplate) {
    const info = templates.find((t) => t.template === template);
    if (info) {
      // Defaults describe the build; keep the chosen deployment target.
      setForm((current) => ({ ...toInput(info.defaults), target: current.target }));
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    update.mutate(form, {
      onSuccess: (saved) => {
        // Keep the editor mounted and adopt the server's normalized values.
        setForm(toInput(saved));
        toast.success("Configuration saved");
      },
      onError: (error) => toast.error(errorMessage(error)),
    });
  }

  const templateLabels = Object.fromEntries(templates.map((t) => [t.template, t.label]));
  const targetLabels = Object.fromEntries(targets.map((t) => [t.target, t.label]));

  return (
    <Card>
      <form onSubmit={handleSubmit}>
        <CardHeader>
          <CardTitle>Deployment configuration</CardTitle>
          <CardDescription>
            How CloudFlow builds and runs this environment. Changes apply to the next deployment.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 py-4 sm:grid-cols-2">
          <FormField id="config-template" label="Template">
            <div className="flex gap-2">
              <Select
                items={templateLabels}
                value={form.template}
                onValueChange={(value) => value && set("template", value as ConfigTemplate)}
                disabled={!canWrite}
              >
                <SelectTrigger id="config-template" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {templates.map((template) => (
                    <SelectItem key={template.template} value={template.template}>
                      {template.label}
                      {template.recommended && " (recommended)"}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {canWrite && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => applyTemplateDefaults(form.template)}
                >
                  Use defaults
                </Button>
              )}
            </div>
          </FormField>
          <FormField
            id="config-target"
            label="Deployment target"
            hint="Images are built with Docker for both targets."
            error={fieldError("target")}
          >
            <Select
              items={targetLabels}
              value={form.target}
              onValueChange={(value) => value && set("target", value as DeploymentTarget)}
              disabled={!canWrite}
            >
              <SelectTrigger id="config-target" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {targets.map((target) => (
                  <SelectItem
                    key={target.target}
                    value={target.target}
                    disabled={!target.available && target.target !== form.target}
                  >
                    {target.label}
                    {!target.available && " (not configured)"}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>
          {isDocker ? (
            <FormField
              id="config-dockerfile"
              label="Dockerfile path"
              error={fieldError("dockerfilePath")}
            >
              <Input
                id="config-dockerfile"
                value={form.dockerfilePath}
                onChange={(e) => set("dockerfilePath", e.target.value)}
                disabled={!canWrite}
                required
              />
            </FormField>
          ) : (
            <FormField
              id="config-runtime"
              label="Runtime version"
              hint="e.g. 21 for Java, 22 for Node.js, 3.12 for Python"
              error={fieldError("runtimeVersion")}
            >
              <Input
                id="config-runtime"
                value={form.runtimeVersion ?? ""}
                onChange={(e) => set("runtimeVersion", e.target.value || null)}
                disabled={!canWrite}
              />
            </FormField>
          )}
          {!isDocker && (
            <>
              <FormField id="config-build" label="Build command" error={fieldError("buildCommand")}>
                <Input
                  id="config-build"
                  className="font-mono text-xs"
                  value={form.buildCommand ?? ""}
                  onChange={(e) => set("buildCommand", e.target.value || null)}
                  disabled={!canWrite}
                />
              </FormField>
              <FormField id="config-start" label="Start command" error={fieldError("startCommand")}>
                <Input
                  id="config-start"
                  className="font-mono text-xs"
                  value={form.startCommand ?? ""}
                  onChange={(e) => set("startCommand", e.target.value || null)}
                  disabled={!canWrite}
                />
              </FormField>
            </>
          )}
          <FormField id="config-port" label="Container port" error={fieldError("containerPort")}>
            <Input
              id="config-port"
              type="number"
              min={1}
              max={65535}
              value={form.containerPort}
              onChange={(e) => set("containerPort", Number(e.target.value))}
              disabled={!canWrite}
              required
            />
          </FormField>
          <FormField
            id="config-health"
            label="Health-check path"
            error={fieldError("healthCheckPath")}
          >
            <Input
              id="config-health"
              value={form.healthCheckPath}
              onChange={(e) => set("healthCheckPath", e.target.value)}
              disabled={!canWrite}
              required
            />
          </FormField>
          <FormField id="config-cpu" label="CPU limit (cores)" error={fieldError("cpuLimit")}>
            <Input
              id="config-cpu"
              type="number"
              step={0.1}
              min={0.1}
              max={8}
              value={form.cpuLimit ?? ""}
              onChange={(e) =>
                set("cpuLimit", e.target.value === "" ? null : Number(e.target.value))
              }
              disabled={!canWrite}
            />
          </FormField>
          <FormField
            id="config-memory"
            label="Memory limit (MB)"
            error={fieldError("memoryLimitMb")}
          >
            <Input
              id="config-memory"
              type="number"
              min={128}
              max={16384}
              value={form.memoryLimitMb ?? ""}
              onChange={(e) =>
                set("memoryLimitMb", e.target.value === "" ? null : Number(e.target.value))
              }
              disabled={!canWrite}
            />
          </FormField>
        </CardContent>
        {canWrite && (
          <CardFooter>
            <Button type="submit" disabled={update.isPending}>
              {update.isPending ? "Saving…" : "Save configuration"}
            </Button>
          </CardFooter>
        )}
      </form>
    </Card>
  );
}

function toInput(config: DeploymentConfig): DeploymentConfigInput {
  return {
    template: config.template,
    runtimeVersion: config.runtimeVersion,
    buildCommand: config.buildCommand,
    startCommand: config.startCommand,
    dockerfilePath: config.dockerfilePath,
    containerPort: config.containerPort,
    healthCheckPath: config.healthCheckPath,
    cpuLimit: config.cpuLimit,
    memoryLimitMb: config.memoryLimitMb,
    target: config.target,
  };
}
