/** Role name with its Discord color. */
export function RoleName({ name, color }: { name: string; color: number | null }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span
        className="size-2.5 shrink-0 rounded-full bg-muted-foreground/40"
        style={color !== null ? { backgroundColor: `#${color.toString(16).padStart(6, '0')}` } : undefined}
      />
      {name}
    </span>
  )
}
