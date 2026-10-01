"use client";

import { useState, type FormEvent, type ReactElement } from "react";
import { toast } from "sonner";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { usePutVariable } from "@/hooks/use-environments";
import { errorMessage } from "@/lib/api/errors";
import type { Variable } from "@/lib/api/types";

const KEY_PATTERN = /^[A-Z_][A-Z0-9_]*$/;

interface VariableDialogProps {
  environmentId: string;
  projectId: string;
  trigger: ReactElement;
  /** When set, the dialog edits this variable; its key cannot change. */
  variable?: Variable;
}

export function VariableDialog({
  environmentId,
  projectId,
  trigger,
  variable,
}: VariableDialogProps) {
  const [open, setOpen] = useState(false);
  const [key, setKey] = useState(variable?.key ?? "");
  const [value, setValue] = useState(variable?.value ?? "");
  const [secret, setSecret] = useState(variable?.secret ?? false);
  const putVariable = usePutVariable(environmentId, projectId);
  const keyValid = KEY_PATTERN.test(key) && !key.startsWith("CLOUDFLOW_");

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setKey(variable?.key ?? "");
      setValue(variable?.value ?? "");
      setSecret(variable?.secret ?? false);
      putVariable.reset();
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    putVariable.mutate(
      { key, value, secret },
      {
        onSuccess: () => {
          toast.success(`${key} saved`);
          setOpen(false);
        },
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={trigger} />
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={handleSubmit} className="grid gap-4">
          <DialogHeader>
            <DialogTitle>{variable ? `Edit ${variable.key}` : "Add variable"}</DialogTitle>
            <DialogDescription>
              {variable?.secret
                ? "Secret values cannot be viewed. Enter a new value to replace it."
                : "Variables are injected into the container at deploy time."}
            </DialogDescription>
          </DialogHeader>
          <FormField
            id="variable-key"
            label="Key"
            hint="Upper-case letters, digits, and underscores. The CLOUDFLOW_ prefix is reserved."
            error={key && !keyValid ? "Invalid key" : undefined}
          >
            <Input
              id="variable-key"
              value={key}
              onChange={(event) => setKey(event.target.value.toUpperCase())}
              disabled={!!variable}
              autoComplete="off"
              required
            />
          </FormField>
          <FormField id="variable-value" label="Value">
            <Textarea
              id="variable-value"
              value={value}
              onChange={(event) => setValue(event.target.value)}
              placeholder={variable?.secret ? "New secret value" : undefined}
              className="font-mono text-xs"
              rows={3}
              required={!!variable?.secret}
            />
          </FormField>
          <div className="flex items-center gap-3">
            <Switch id="variable-secret" checked={secret} onCheckedChange={setSecret} />
            <Label htmlFor="variable-secret">Secret (encrypted, write-only)</Label>
          </div>
          {putVariable.isError && (
            <p className="text-sm text-destructive">{errorMessage(putVariable.error)}</p>
          )}
          <DialogFooter>
            <Button type="submit" disabled={putVariable.isPending || !keyValid}>
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
