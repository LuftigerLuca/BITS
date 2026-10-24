/**
 * Follows the system's light or dark setting, also when it changes while the dashboard is open. The dark colors are
 * the `.dark` block of index.css. An inline script in index.html would apply them before the first paint, but the
 * CSP of nginx allows no inline scripts, so this runs first thing in main.tsx instead.
 */
export function followSystemTheme() {
  const dark = window.matchMedia('(prefers-color-scheme: dark)')
  const apply = () => document.documentElement.classList.toggle('dark', dark.matches)
  apply()
  dark.addEventListener('change', apply)
}
