import { useSyncExternalStore, type CSSProperties } from "react"
import { CircleCheckIcon, CircleXIcon, TriangleAlertIcon, XIcon } from "lucide-react"
import { Toaster as Sonner, type ToasterProps } from "sonner"

/** Whether the dashboard shows its dark colors; theme.ts toggles the class, without next-themes. */
function subscribeDark(onChange: () => void) {
  const observer = new MutationObserver(onChange)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] })
  return () => observer.disconnect()
}

const isDark = () => document.documentElement.classList.contains("dark")

/** The type of a toast shows in the shape and color of its icon; the toast itself stays neutral. */
function Toaster(props: ToasterProps) {
  const dark = useSyncExternalStore(subscribeDark, isDark)
  return (
    <Sonner
      theme={dark ? "dark" : "light"}
      className="toaster group"
      closeButton
      containerAriaLabel="Benachrichtigungen"
      icons={{
        success: <CircleCheckIcon className="size-4 text-green-600 dark:text-green-500" />,
        warning: <TriangleAlertIcon className="size-4 text-amber-500" />,
        error: <CircleXIcon className="size-4 text-destructive" />,
        close: <XIcon className="size-3" />,
      }}
      style={
        {
          "--normal-bg": "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
          "--border-radius": "var(--radius)",
        } as CSSProperties
      }
      toastOptions={{
        closeButtonAriaLabel: "Schließen",
        classNames: { description: "!text-muted-foreground" },
      }}
      {...props}
    />
  )
}

export { Toaster }
