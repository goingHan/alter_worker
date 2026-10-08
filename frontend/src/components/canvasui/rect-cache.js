// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260924: cache the element rect so a pointer sweep does not force a synchronous layout on every move
const ZERO_RECT = { x: 0, y: 0, width: 0, height: 0, top: 0, right: 0, bottom: 0, left: 0 }

export function createRectCache (el) {
  let rect = null
  let dirty = true

  const invalidate = () => { dirty = true }

  const observer = el ? new ResizeObserver(invalidate) : null
  if (observer) observer.observe(el)

  window.addEventListener('scroll', invalidate, { capture: true, passive: true })
  window.addEventListener('resize', invalidate, { passive: true })
  window.addEventListener('orientationchange', invalidate)

  return {
    get current () {
      if (!el) return ZERO_RECT
      if (dirty || !rect) {
        rect = el.getBoundingClientRect()
        dirty = false
      }
      return rect
    },
    destroy () {
      window.removeEventListener('scroll', invalidate, { capture: true })
      window.removeEventListener('resize', invalidate)
      window.removeEventListener('orientationchange', invalidate)
      if (observer) observer.disconnect()
      rect = null
      dirty = true
    }
  }
}
