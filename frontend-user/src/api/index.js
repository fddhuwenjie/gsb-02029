import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('user_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('user_token')
      localStorage.removeItem('user_info')
      if (window.__showLoginModal) {
        window.__showLoginModal()
      }
    }
    return Promise.reject(error)
  }
)

export default api
