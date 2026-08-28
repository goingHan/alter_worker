import axios from 'axios'
// #AI-CODE-dev_release/v0.0.1-20260805: fix import - iview -> view-ui-plus (Vue 3)
import { Message } from 'view-ui-plus'

// #AI-CODE-dev_release/v0.0.1-20260604: add global error handler with iView notifications
const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

request.interceptors.response.use(
  response => response,
  error => {
    let errorMessage = 'An error occurred'

    if (error.response) {
      const status = error.response.status
      const data = error.response.data

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

    Message.error({
      content: errorMessage,
      duration: 5
    })

    return Promise.reject(error)
  }
)

export default request
