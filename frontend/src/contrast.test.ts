/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { contrast, over, parseColor, tokens, type Rgb } from './test/contrast'

/*
 * axe cannot measure contrast in jsdom, so this test computes it from the colour tokens: every text colour on the
 * backgrounds the dashboard shows it on, including the tints of buttons and badges and their hover states. Text needs
 * 4.5:1 (WCAG AA), icons 3:1.
 */
// Read from disk, since Vitest stubs imported CSS.
const css = readFileSync('src/index.css', 'utf8')
const light = tokens(css, ':root')
const themes = { light, dark: { ...light, ...tokens(css, '.dark') } }

describe.each(Object.entries(themes))('contrast in the %s theme', (theme, values) => {
  const color = (name: string) => parseColor(values[name])
  const card = color('card')
  const tint = (name: string, alpha: number): Rgb => over(color(name), card, alpha)
  const dark = theme === 'dark'
  const text: [string, string, Rgb, Rgb][] = [
    ['foreground', 'background', color('foreground'), color('background')],
    ['foreground', 'muted', color('foreground'), color('muted')],
    ['muted-foreground', 'background', color('muted-foreground'), color('background')],
    ['muted-foreground', 'card', color('muted-foreground'), card],
    ['muted-foreground', 'muted', color('muted-foreground'), color('muted')],
    ['muted-foreground', 'muted/50', color('muted-foreground'), tint('muted', 0.5)],
    ['muted-foreground', 'sidebar', color('muted-foreground'), color('sidebar')],
    ['accent-foreground', 'accent', color('accent-foreground'), color('accent')],
    ['secondary-foreground', 'secondary', color('secondary-foreground'), color('secondary')],
    ['sidebar-foreground', 'sidebar', color('sidebar-foreground'), color('sidebar')],
    ['sidebar-primary-foreground', 'sidebar-primary', color('sidebar-primary-foreground'), color('sidebar-primary')],
    ['primary-foreground', 'primary', color('primary-foreground'), color('primary')],
    ['discord-foreground', 'discord', color('discord-foreground'), color('discord')],
    ['destructive', 'card', color('destructive'), card],
    ['destructive/90', 'card', over(color('destructive'), card, 0.9), card],
    // Destructive buttons and badges: bg-destructive/10 and hover /15 in light, /15 and hover /20 in dark.
    ['destructive', 'its rest tint', color('destructive'), tint('destructive', dark ? 0.15 : 0.1)],
    ['destructive', 'its hover tint', color('destructive'), tint('destructive', dark ? 0.2 : 0.15)],
    ['success', 'card', color('success'), card],
    ['success', 'success/10', color('success'), tint('success', 0.1)],
    ['warning', 'card', color('warning'), card],
    ['warning', 'warning/10', color('warning'), tint('warning', 0.1)],
  ]

  it.each(text)('%s on %s reaches 4.5:1', (_foreground, _background, foreground, background) => {
    expect(contrast(foreground, background)).toBeGreaterThanOrEqual(4.5)
  })

  // Primary is no text colour, see index.css; it only colours icons.
  it.each([
    ['card', card],
    ['muted', color('muted')],
    ['background', color('background')],
  ])('primary icons on %s reach 3:1', (_, background) => {
    expect(contrast(color('primary'), background)).toBeGreaterThanOrEqual(3)
  })
})
