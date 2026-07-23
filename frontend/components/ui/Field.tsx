"use client";

import { useId, type InputHTMLAttributes, type ReactNode } from "react";

type FieldProps = {
  label: string;
  error?: string | null;
  hint?: string;
  className?: string;
  inputClassName?: string;
  children?: ReactNode;
} & Omit<InputHTMLAttributes<HTMLInputElement>, "className" | "children">;

export function Field({
  label,
  error,
  hint,
  className = "",
  inputClassName = "",
  id,
  children,
  ...inputProps
}: FieldProps) {
  const autoId = useId();
  const fieldId = id ?? autoId;
  const errorId = `${fieldId}-error`;
  const hintId = `${fieldId}-hint`;
  const describedBy = [
    error ? errorId : null,
    hint && !error ? hintId : null,
  ]
    .filter(Boolean)
    .join(" ") || undefined;

  return (
    <label className={`field ${className}`} htmlFor={fieldId}>
      <span className="field-label">{label}</span>
      {children ?? (
        <input
          id={fieldId}
          className={`input-field ${inputClassName}`}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          {...inputProps}
        />
      )}
      {error ? (
        <span id={errorId} className="field-error" role="alert">
          {error}
        </span>
      ) : hint ? (
        <span id={hintId} className="field-hint">
          {hint}
        </span>
      ) : null}
    </label>
  );
}
