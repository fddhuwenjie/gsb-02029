import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code && data.code !== 200) {
      return Promise.reject({ response: { data } })
    }
    return data
  },
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('admin_token')
      ElMessage.error('登录已失效，请重新登录')
      setTimeout(() => {
        window.location.href = '/login'
      }, 1500)
    }
    return Promise.reject(error)
  }
)

export default api
