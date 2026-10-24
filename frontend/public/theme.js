// Applies the stored theme before the first paint, so no wrong theme flashes up. It is a file of its own because the
// CSP allows no inline scripts; src/lib/theme.ts takes over once the app runs and stores the choice under `theme`.
try {
  var theme = localStorage.getItem('theme')
  var dark = theme === 'dark' || (theme !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', dark)
} catch {
  // Blocked storage: src/lib/theme.ts falls back to the system setting.
  document.documentElement.classList.toggle('dark', window.matchMedia('(prefers-color-scheme: dark)').matches)
}
