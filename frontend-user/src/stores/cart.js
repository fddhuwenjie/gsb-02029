import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '../api'

export const useCartStore = defineStore('cart', () => {
  const items = ref([])
  
  const count = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))
  const total = computed(() => items.value.reduce((sum, item) => sum + item.book?.price * item.quantity, 0))
  
  const fetchCart = async () => {
    try {
      const res = await api.get('/cart')
      if (res.code === 200) {
        items.value = res.data
      }
    } catch (e) {
      console.error(e)
    }
  }
  
  const addToCart = async (bookId) => {
    try {
      await api.post('/cart', { bookId })
      await fetchCart()
      return true
    } catch (e) {
      return false
    }
  }
  
  const updateQuantity = async (id, quantity) => {
    try {
      await api.put(`/cart/${id}`, { quantity })
      await fetchCart()
    } catch (e) {
      console.error(e)
    }
  }
  
  const removeItem = async (id) => {
    try {
      await api.delete(`/cart/${id}`)
      await fetchCart()
    } catch (e) {
      console.error(e)
    }
  }
  
  const clearCart = async () => {
    try {
      await api.delete('/cart')
      items.value = []
    } catch (e) {
      console.error(e)
    }
  }
  
  return { items, count, total, fetchCart, addToCart, updateQuantity, removeItem, clearCart }
})
