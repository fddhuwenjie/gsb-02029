import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('user_token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('user_info') || 'null'))
  
  const isLoggedIn = computed(() => !!token.value)
  
  const setUser = (data) => {
    token.value = data.token
    userInfo.value = data.user
    localStorage.setItem('user_token', data.token)
    localStorage.setItem('user_info', JSON.stringify(data.user))
  }
  
  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('user_token')
    localStorage.removeItem('user_info')
  }
  
  return { token, userInfo, isLoggedIn, setUser, logout }
})
