<template>
  <div class="cart-page">
    <div class="container">
      <h1 class="page-title">购物车</h1>
      
      <div class="cart-content" v-if="cartStore.items.length">
        <div class="cart-items">
          <div class="cart-item" v-for="item in cartStore.items" :key="item.id">
            <img :src="item.book?.coverImage || 'https://via.placeholder.com/100x140'" class="item-cover" />
            <div class="item-info">
              <h3>{{ item.book?.title }}</h3>
              <p>{{ item.book?.author }}</p>
              <span class="item-price">¥{{ item.book?.price }}</span>
            </div>
            <div class="item-quantity">
              <button @click="updateQuantity(item, -1)" :disabled="item.quantity <= 1">-</button>
              <span>{{ item.quantity }}</span>
              <button @click="updateQuantity(item, 1)">+</button>
            </div>
            <div class="item-total">
              ¥{{ (item.book?.price * item.quantity).toFixed(2) }}
            </div>
            <button class="remove-btn" @click="removeItem(item.id)">×</button>
          </div>
        </div>
        
        <div class="cart-summary">
          <div class="summary-card">
            <h3>订单摘要</h3>
            <div class="summary-row">
              <span>商品数量</span>
              <span>{{ cartStore.count }} 件</span>
            </div>
            <div class="summary-row total">
              <span>合计</span>
              <span class="total-price">¥{{ cartStore.total.toFixed(2) }}</span>
            </div>
            <button class="btn btn-primary btn-lg" style="width: 100%" @click="checkout">
              去结算
            </button>
          </div>
        </div>
      </div>
      
      <div class="empty" v-else>
        <p>购物车是空的</p>
        <router-link to="/books" class="btn btn-primary" style="margin-top: 16px">去逛逛</router-link>
      </div>
    </div>

    <!-- Checkout Modal -->
    <div class="modal-overlay" v-if="showCheckout" @click.self="showCheckout = false">
      <div class="modal">
        <h2>确认订单</h2>
        <form @submit.prevent="submitOrder">
          <div class="form-group">
            <label>收货人</label>
            <input type="text" v-model="orderForm.receiver" class="input" required />
          </div>
          <div class="form-group">
            <label>联系电话</label>
            <input type="tel" v-model="orderForm.phone" class="input" required />
          </div>
          <div class="form-group">
            <label>收货地址</label>
            <textarea v-model="orderForm.address" class="input" rows="3" required></textarea>
          </div>
          <div class="form-group">
            <label>备注</label>
            <textarea v-model="orderForm.remark" class="input" rows="2"></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="showCheckout = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">
              {{ submitting ? '提交中...' : '提交订单' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '../stores/cart'
import { useToast } from '../composables/useToast'
import api from '../api'

const router = useRouter()
const cartStore = useCartStore()
const toast = useToast()

const showCheckout = ref(false)
const showConfirm = ref(false)
const confirmAction = ref(null)
const submitting = ref(false)
const orderForm = ref({
  receiver: '',
  phone: '',
  address: '',
  remark: ''
})

const updateQuantity = (item, delta) => {
  const newQty = item.quantity + delta
  if (newQty >= 1) {
    cartStore.updateQuantity(item.id, newQty)
  }
}

const removeItem = (id) => {
  cartStore.removeItem(id)
  toast.success('已从购物车移除')
}

const checkout = () => {
  showCheckout.value = true
}

const submitOrder = async () => {
  submitting.value = true
  try {
    const items = cartStore.items.map(item => ({
      bookId: item.bookId,
      quantity: item.quantity
    }))
    
    const res = await api.post('/orders', {
      ...orderForm.value,
      items
    })
    
    if (res.code === 200) {
      await cartStore.clearCart()
      toast.success('订单提交成功！')
      showCheckout.value = false
      router.push('/orders')
    } else {
      toast.error(res.message || '提交失败')
    }
  } catch (e) {
    toast.error('提交失败')
  }
  submitting.value = false
}

onMounted(() => {
  cartStore.fetchCart()
})
</script>

<style scoped>
.cart-page {
  padding: 40px 0;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--gray-800);
  margin-bottom: 32px;
}

.cart-content {
  display: flex;
  gap: 32px;
}

.cart-items {
  flex: 1;
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  margin-bottom: 16px;
}

.item-cover {
  width: 80px;
  height: 110px;
  object-fit: cover;
  border-radius: var(--radius-sm);
}

.item-info {
  flex: 1;
}

.item-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 4px;
}

.item-info p {
  font-size: 14px;
  color: var(--gray-500);
  margin-bottom: 8px;
}

.item-price {
  color: var(--accent);
  font-weight: 500;
}

.item-quantity {
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-quantity button {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--gray-100);
  color: var(--gray-700);
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.item-quantity button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.item-total {
  font-size: 18px;
  font-weight: 600;
  color: var(--gray-800);
  min-width: 100px;
  text-align: right;
}

.remove-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--gray-100);
  color: var(--gray-500);
  font-size: 20px;
}

.remove-btn:hover {
  background: var(--danger);
  color: white;
}

.cart-summary {
  width: 320px;
}

.summary-card {
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 24px;
  position: sticky;
  top: 100px;
}

.summary-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 20px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--gray-100);
  color: var(--gray-600);
}

.summary-row.total {
  border-bottom: none;
  margin-bottom: 20px;
}

.total-price {
  font-size: 24px;
  font-weight: 700;
  color: var(--accent);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: white;
  border-radius: var(--radius);
  padding: 32px;
  width: 100%;
  max-width: 480px;
}

.modal h2 {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-700);
  margin-bottom: 8px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
</style>
