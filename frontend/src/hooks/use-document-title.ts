import { useEffect } from 'react'

/** Names the browser tab after the page, e.g. „Rollen – BITS“, so screen readers announce where a link led. */
export function useDocumentTitle(...parts: string[]) {
  const title = [...parts, 'BITS'].join(' – ')
  useEffect(() => {
    document.title = title
    return () => {
      document.title = 'BITS'
    }
  }, [title])
}
