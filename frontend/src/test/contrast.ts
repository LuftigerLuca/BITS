/** An sRGB colour with channels from 0 to 1, as the browser renders a CSS colour. */
export type Rgb = [number, number, number]

/** The custom properties of [selector] in [css], e.g. the colour tokens of `:root` or `.dark`. */
export function tokens(css: string, selector: string): Record<string, string> {
  const escaped = selector.replace('.', '\\.')
  const body = css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`))?.[1] ?? ''
  return Object.fromEntries([...body.matchAll(/--([a-z-]+):\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]))
}

/** A colour in `#rrggbb` or `oklch(L C H)` notation, converted and clipped to sRGB like the browser does. */
export function parseColor(value: string): Rgb {
  if (value.startsWith('#')) return [0, 2, 4].map((i) => parseInt(value.slice(1 + i, 3 + i), 16) / 255) as Rgb
  const m = value.match(/oklch\(([\d.]+)\s+([\d.]+)\s+([\d.]+)/)
  if (!m) throw new Error(`Unknown colour ${value}`)
  const [lightness, chroma, hue] = [Number(m[1]), Number(m[2]), (Number(m[3]) * Math.PI) / 180]
  const [a, b] = [chroma * Math.cos(hue), chroma * Math.sin(hue)]
  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const mid = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const s = (lightness - 0.0894841775 * a - 1.291485548 * b) ** 3
  const linear = [
    4.0767416621 * l - 3.3077115913 * mid + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * mid - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * mid + 1.707614701 * s,
  ]
  return linear.map((c) => {
    const clipped = Math.min(1, Math.max(0, c))
    return clipped <= 0.0031308 ? 12.92 * clipped : 1.055 * clipped ** (1 / 2.4) - 0.055
  }) as Rgb
}

/** [color] with [alpha] on top of [background], e.g. `bg-destructive/10` on a card. */
export function over(color: Rgb, background: Rgb, alpha: number): Rgb {
  return color.map((c, i) => c * alpha + background[i] * (1 - alpha)) as Rgb
}

/** The contrast ratio of WCAG 2.2. */
export function contrast(first: Rgb, second: Rgb): number {
  const luminance = (c: Rgb) =>
    c
      .map((x) => (x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4))
      .reduce((sum, x, i) => sum + x * [0.2126, 0.7152, 0.0722][i], 0)
  const [lighter, darker] = [luminance(first), luminance(second)].sort((p, q) => q - p)
  return (lighter + 0.05) / (darker + 0.05)
}
