import { createApp } from 'vue'
import ViewUIPlus from 'view-ui-plus'
import 'view-ui-plus/dist/styles/viewuiplus.css'
import './styles/theme.css'
import App from './App.vue'
import router from './router'
import { applyTheme, getThemeKey } from './theme'

applyTheme(getThemeKey())

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: 最小化/遮挡时给 <html> 挂 page-hidden 类，
// 配合 theme.css 暂停所有 CSS 动画，避免常驻合成层干扰浏览器最小化
document.addEventListener('visibilitychange', () => {
  document.documentElement.classList.toggle('page-hidden', document.hidden)
})

createApp(App).use(router).use(ViewUIPlus).mount('#app')
