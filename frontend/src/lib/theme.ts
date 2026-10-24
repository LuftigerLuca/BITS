/** The theme a user picked; `system` follows the light or dark setting of the system. */
export type Theme = 'system' | 'light' | 'dark'

/** Also read by public/theme.js, which applies the stored theme before the first paint. */
const STORAGE_KEY = 'theme'

const systemDark = () => window.matchMedia('(prefers-color-scheme: dark)')

/** The stored theme, `system` if there is none or the storage is blocked. */
export function storedTheme(): Theme {
  try {
    const theme = localStorage.getItem(STORAGE_KEY)
    if (theme === 'light' || theme === 'dark') return theme
  } catch {
    // Blocked storage, e.g. in a private window of some browsers.
  }
  return 'system'
}

/** Applies [theme] right away and keeps it for the next visit. */
export function setTheme(theme: Theme) {
  try {
    if (theme === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Without storage the choice lasts until the page is reloaded.
  }
  applyTheme(theme)
}

function applyTheme(theme: Theme) {
  const dark = theme === 'dark' || (theme === 'system' && systemDark().matches)
  document.documentElement.classList.toggle('dark', dark)
}

/**
 * Applies the stored theme and, while it is `system`, follows changes of the system setting while the dashboard is
 * open. The dark colors are the `.dark` block of index.css. public/theme.js has already applied them before the first
 * paint, since the CSP of nginx allows no inline scripts; this runs first thing in main.tsx.
 */
export function followTheme() {
  applyTheme(storedTheme())
  systemDark().addEventListener('change', () => {
    if (storedTheme() === 'system') applyTheme('system')
  })
}
