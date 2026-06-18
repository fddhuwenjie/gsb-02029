import { ref } from 'vue'

const toastState = ref({
  show: false,
  message: '',
  type: 'info'
})

export function useToast() {
  const showToast = (message, type = 'info') => {
    toastState.value = { show: true, message, type }
  }
  
  const success = (message) => showToast(message, 'success')
  const error = (message) => showToast(message, 'error')
  const info = (message) => showToast(message, 'info')
  const warning = (message) => showToast(message, 'warning')
  
  const hideToast = () => {
    toastState.value.show = false
  }
  
  return {
    toastState,
    showToast,
    success,
    error,
    info,
    warning,
    hideToast
  }
}
