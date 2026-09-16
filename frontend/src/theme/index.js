const THEMES = [
  {
    key: 'green-sorrow',
    name: '绿色忧伤',
    dark: true,
    desc: '幽绿 · 护眼 · 守望'
  },
  {
    key: 'dawn',
    name: '晨光',
    dark: false,
    desc: '清爽 · 明亮 · 轻快'
  },
  {
    key: 'midnight',
    name: '子夜',
    dark: true,
    desc: '静谧 · 深邃 · 专注'
  }
]

const STORAGE_KEY = 'alertworker-theme'

export function getThemeKey () {
  const saved = localStorage.getItem(STORAGE_KEY)
  return saved && THEMES.some(t => t.key === saved) ? saved : 'green-sorrow'
}

export function applyTheme (key) {
  const theme = THEMES.find(t => t.key === key) || THEMES[0]
  document.documentElement.setAttribute('data-theme', theme.key)
  localStorage.setItem(STORAGE_KEY, theme.key)
  return theme
}

export function listThemes () {
  return THEMES
}
