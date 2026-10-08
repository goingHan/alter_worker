import axios from 'axios'
import { Message } from 'view-ui-plus'
import { getToken, clearAuth } from '../utils/auth'

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260908: per-module URL prefixes —
// 工程前缀 /pw/alertworker + 模块前缀（datamanager=/dm，gateway=/gw，见各后端 application.yml server.servlet.context-path）
const createRequest = (baseURL) => {
  const instance = axios.create({
    baseURL,
    timeout: 30000
  })

  instance.interceptors.request.use(config => {
    const token = getToken()
    if (token) {
      config.headers['X-Admin-Token'] = token
    }
    return config
  })

  instance.interceptors.response.use(
    response => response,
    error => {
      let errorMessage = 'An error occurred'

      if (error.response) {
        const status = error.response.status
        const data = error.response.data

        if (status === 401 && !error.config.url.includes('/auth/login')) {
          clearAuth()
          if (window.location.pathname !== import.meta.env.BASE_URL + 'login') {
            window.location.href = import.meta.env.BASE_URL + 'login'
          }
        }

        switch (status) {
          case 400:
            errorMessage = data?.message || 'Invalid request parameters'
            break
          case 401:
            errorMessage = 'Unauthorized - please login again'
            break
          case 403:
            errorMessage = 'Access forbidden'
            break
          case 404:
            errorMessage = 'Resource not found'
            break
          case 422:
            errorMessage = data?.message || 'Validation failed'
            break
          case 500:
            errorMessage = 'Internal server error'
            break
          default:
            errorMessage = data?.message || `Request failed (${status})`
        }
      } else if (error.request) {
        errorMessage = 'No response from server - please check your connection'
      } else {
        errorMessage = error.message
      }

      if (error.config?.url && !error.config.url.includes('/auth/login')) {
        Message.error({
          content: errorMessage,
          duration: 5
        })
      }

      return Promise.reject(error)
    }
  )

  return instance
}

// datamanager：配置 CRUD / 告警历史 / 登录等
const request = createRequest('/pw/alertworker/dm/api/v1')

// gateway：告警接入与通道测试等
export const gatewayRequest = createRequest('/pw/alertworker/gw/api/v1')

export default request
