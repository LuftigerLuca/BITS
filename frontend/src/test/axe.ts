import axe from 'axe-core'
import { expect } from 'vitest'

/**
 * Fails with every violation axe finds in [context]. jsdom does not lay out the page, so contrast is left to
 * `contrast.test.ts`. With [popup], a menu or a dialog is open: it is rendered at the end of the body like every popup,
 * outside the landmarks, so the rule that all content sits in a landmark is skipped.
 */
export async function expectNoAxeViolations(context: Element = document.body, { popup = false } = {}) {
  const results = await axe.run(context, {
    rules: { 'color-contrast': { enabled: false }, ...(popup ? { region: { enabled: false } } : {}) },
  })
  expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((node) => node.target.join(' ')).join(', ')}`)).toEqual(
    [],
  )
}
