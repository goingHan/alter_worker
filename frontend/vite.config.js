import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 默认：datamanager 8083（与 application.yml server.port 一致）/ gateway 8081 / processor 8084；可用环境变量覆盖
const datamanagerTarget = process.env.VITE_PROXY_DATAMANAGER || 'http://localhost:8083'
const gatewayTarget = process.env.VITE_PROXY_GATEWAY || 'http://localhost:8081'
const processorTarget = process.env.VITE_PROXY_PROCESSOR || 'http://localhost:8084'

export default defineConfig({
  plugins: [vue()],
  // 前端上下文路径：与后端一致使用「工程前缀 /pw/alertworker」+ /ui，
  // 便于同一 nginx 下部署多个前端项目；构建产物内资源引用均带此前缀
  base: '/pw/alertworker/ui/',
  server: {
    port: 3000,
    // dev 模式下页面与代理均挂在 base 下：http://localhost:3000/pw/alertworker/ui/
    proxy: {
      // 各后端 context-path = 工程前缀 /pw/alertworker + 模块前缀（/dm、/gw），
      // 前缀即应用标识，无需 rewrite，直接整段转发
      '/pw/alertworker/dm': {
        target: datamanagerTarget,
        changeOrigin: true
      },
      '/pw/alertworker/gw': {
        target: gatewayTarget,
        changeOrigin: true
      },
      '/pw/alertworker/dp': {
        target: processorTarget,
        changeOrigin: true
      }
    }
  }
})
