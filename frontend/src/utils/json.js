// #AI-CODE-FILE-dev_release/v0.0.1-20260901: pretty-print JSON for editors and display
export function prettyJson (str) {
  if (!str) return str || ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch (e) {
    return str
  }
}

export function isHtml (str) {
  if (!str) return false
  const t = str.trim()
  return t.startsWith('<') && t.includes('>')
}
