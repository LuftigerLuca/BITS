import { useEffect, useState } from 'react'

// A section counts as being read once its heading has passed this far below the top of the window.
const READING_LINE_PX = 120

/**
 * Which of the sections with the given ids is being read: the last one whose start has scrolled past the reading
 * line, or the first while none has. At the very bottom of the page it is the last one, which may be too short to
 * ever reach the line.
 */
export function useActiveSection(ids: readonly string[]): string | undefined {
  const [active, setActive] = useState<string | undefined>(ids[0])

  useEffect(() => {
    let frame = 0
    const update = () => {
      frame = 0
      const sections = ids
        .map((id) => document.getElementById(id))
        .filter((section): section is HTMLElement => section !== null)
      if (sections.length === 0) return
      const atBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2
      const current = atBottom
        ? sections[sections.length - 1]
        : sections.findLast((section) => section.getBoundingClientRect().top <= READING_LINE_PX)
      setActive((current ?? sections[0]).id)
    }
    const schedule = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }

    update()
    window.addEventListener('scroll', schedule, { passive: true })
    window.addEventListener('resize', schedule)
    return () => {
      window.removeEventListener('scroll', schedule)
      window.removeEventListener('resize', schedule)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [ids])

  return active
}
