import type { ButtonHTMLAttributes } from "react";

export const buttonStyles = {
  primary: "bg-primary text-surface hover:bg-primary-strong",
  secondary: "border border-line bg-surface text-ink hover:border-primary",
  danger: "bg-danger-bg text-danger hover:opacity-80",
};

export default function Button({ variant = "primary", className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: keyof typeof buttonStyles }) {
  return <button {...props} className={`inline-flex min-h-12 items-center justify-center gap-2 rounded-xl px-4 py-3 text-sm font-bold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 ${buttonStyles[variant]} ${className}`} />;
}
