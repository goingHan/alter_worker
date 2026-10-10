// #AI-CODE-FILE-dev_release/v0.0.1-20261008: centralize time formatting to Asia/Shanghai (yyyy-MM-dd HH:mm:ss)
// Backend runs in Asia/Shanghai; API times arrive either as bare CST wall-clock strings
// (e.g. 2026-10-08T15:58:07.314) or as offset-bearing ISO strings (e.g. 2026-10-08T15:58:07.314+08:00).

const SHANGHAI_FORMATTER = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
})

// True when the string carries a timezone designator (Z / z) or a numeric offset (+08:00 / +0800).
const hasZoneOrOffset = (s) => /[zZ]$/.test(s) || /[+-]\d{2}:?\d{2}$/.test(s)

// '2026/10/08 15:58:07' (zh-CN Intl output) -> '2026-10-08 15:58:07'
const toStandardFormat = (s) => s.replace(/\//g, '-')

// Formats a time value as 'yyyy-MM-dd HH:mm:ss' in Asia/Shanghai.
// - null / empty          -> '--'
// - offset-bearing string -> parsed, converted to Asia/Shanghai
// - bare wall-clock       -> already CST, plain string cleanup
export const fmtTime = (t) => {
  if (!t) return '--'
  const s = String(t).trim()
  if (!s) return '--'
  if (hasZoneOrOffset(s)) {
    const d = new Date(s)
    if (isNaN(d.getTime())) return '--'
    return toStandardFormat(SHANGHAI_FORMATTER.format(d))
  }
  return s.replace('T', ' ').slice(0, 19)
}

// Date-only variant (yyyy-MM-dd) in Asia/Shanghai.
export const fmtDate = (t) => fmtTime(t).slice(0, 10)
