<template>
  <div class="orders-page">
    <div class="container">
      <h1 class="page-title">我的订单</h1>
      
      <div class="orders-list" v-if="orders.length">
        <div class="order-card" v-for="order in orders" :key="order.id">
          <div class="order-header">
            <div class="order-info">
              <span class="order-no">订单号: {{ order.orderNo }}</span>
              <span class="order-time">{{ formatDate(order.createdAt) }}</span>
            </div>
            <span class="order-status" :class="order.status.toLowerCase()">
              {{ statusText[order.status] }}
            </span>
          </div>
          <div class="order-body">
            <div class="order-items">
              <div class="order-item" v-for="item in order.items" :key="item.id">
                <img :src="item.book?.coverImage || 'https://via.placeholder.com/60x80'" />
                <div class="item-info">
                  <span class="item-title">{{ item.book?.title }}</span>
                  <span class="item-qty">x{{ item.quantity }}</span>
                </div>
                <span class="item-price">¥{{ item.price }}</span>
              </div>
            </div>
            <div class="tracking-info" v-if="order.trackingNo && (order.status === 'SHIPPED' || order.status === 'COMPLETED')">
              <span class="tracking-label">快递单号：</span>
              <span class="tracking-no">{{ order.trackingNo }}</span>
            </div>
            <div class="order-total">
              <span>共 {{ order.items?.length || 0 }} 件商品</span>
              <span class="total-amount">合计: <strong>¥{{ order.totalAmount }}</strong></span>
            </div>
          </div>
          <div class="order-footer" v-if="order.status === 'PENDING'">
            <button class="btn btn-secondary" @click="cancelOrder(order)">取消订单</button>
            <button class="btn btn-primary" @click="payOrder(order)">立即支付</button>
          </div>
        </div>
      </div>
      
      <div class="empty" v-else-if="!loading">
        <p>暂无订单</p>
        <router-link to="/books" class="btn btn-primary" style="margin-top: 16px">去购物</router-link>
      </div>
      
      <div class="loading" v-else>
        <div class="spinner"></div>
      </div>

      <!-- 取消订单确认弹窗 -->
      <div class="modal-overlay" v-if="showConfirm" @click.self="showConfirm = false">
        <div class="confirm-dialog">
          <div class="confirm-icon">⚠️</div>
          <h3>确认取消</h3>
          <p>确定要取消该订单吗？</p>
          <div class="confirm-actions">
            <button class="btn btn-secondary" @click="showConfirm = false">返回</button>
            <button class="btn btn-danger" @click="confirmCancel">确定取消</button>
          </div>
        </div>
      </div>

      <!-- 支付弹窗 -->
      <div class="modal-overlay" v-if="showPayment" @click.self="showPayment = false">
        <div class="payment-dialog">
          <div class="payment-header">
            <h3>选择支付方式</h3>
            <button class="close-btn" @click="showPayment = false">×</button>
          </div>
          <div class="payment-amount">
            <span>支付金额</span>
            <strong>¥{{ currentOrder?.totalAmount }}</strong>
          </div>
          <div class="payment-methods">
            <div 
              class="payment-method" 
              :class="{ active: selectedMethod === 'WECHAT' }"
              @click="selectedMethod = 'WECHAT'"
            >
              <div class="method-icon wechat">
                <svg viewBox="0 0 24 24" width="32" height="32">
                  <path fill="#07C160" d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm5.34 2.867c-1.797-.052-3.746.512-5.28 1.786-1.72 1.428-2.687 3.72-1.78 6.22.942 2.453 3.666 4.229 6.884 4.229.826 0 1.622-.12 2.361-.336a.722.722 0 0 1 .598.082l1.584.926a.272.272 0 0 0 .14.047c.134 0 .24-.111.24-.247 0-.06-.023-.12-.038-.177l-.327-1.233a.582.582 0 0 1-.023-.156.49.49 0 0 1 .201-.398C23.024 18.48 24 16.82 24 14.98c0-3.21-2.931-5.837-6.656-6.088V8.89c-.135-.01-.269-.03-.407-.03zm-2.53 3.274c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.97-.982zm4.844 0c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.969-.982z"/>
                </svg>
              </div>
              <div class="method-info">
                <span class="method-name">微信支付</span>
                <span class="method-desc">推荐使用微信扫码支付</span>
              </div>
              <div class="method-check" v-if="selectedMethod === 'WECHAT'">✓</div>
            </div>
            <div 
              class="payment-method" 
              :class="{ active: selectedMethod === 'ALIPAY' }"
              @click="selectedMethod = 'ALIPAY'"
            >
              <div class="method-icon alipay">
                <svg viewBox="0 0 24 24" width="32" height="32">
                  <path fill="#1677FF" d="M21.422 15.358c-1.327-.527-5.126-2.024-7.45-2.907.756-1.263 1.35-2.723 1.73-4.313h-4.207V6.871h5.064V5.93h-5.064V3.257h-2.08c-.231 0-.42.188-.42.42v2.253H4.02v.942h4.975v1.267H4.855v.942h8.64c-.312 1.167-.756 2.239-1.31 3.186-2.108-.67-4.443-1.058-6.893-.832-3.322.307-5.36 2.09-5.36 4.158 0 2.486 2.785 4.016 6.305 4.016 3.266 0 6.114-1.61 8.093-4.2 1.973.942 6.58 2.97 7.307 3.312.727.34 1.363.51 1.363.51V15.36s-.25-.002-1.578-.002zM6.237 18.667c-2.69 0-4.016-1.263-4.016-2.526 0-1.263 1.326-2.526 3.52-2.526 1.95 0 3.9.42 5.64 1.137-1.578 2.4-3.52 3.915-5.144 3.915z"/>
                </svg>
              </div>
              <div class="method-info">
                <span class="method-name">支付宝</span>
                <span class="method-desc">支持花呗分期付款</span>
              </div>
              <div class="method-check" v-if="selectedMethod === 'ALIPAY'">✓</div>
            </div>
          </div>
          <div class="mock-notice" v-if="paymentMode === 'mock'">
            <span class="notice-icon">ℹ️</span>
            <span>当前为模拟支付模式，点击确认即可完成支付</span>
          </div>
          <div class="payment-actions">
            <button class="btn btn-secondary" @click="showPayment = false">取消</button>
            <button class="btn btn-primary btn-pay" @click="confirmPayment" :disabled="paying">
              {{ paying ? '支付中...' : '确认支付' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 支付成功弹窗 -->
      <div class="modal-overlay" v-if="showSuccess" @click.self="showSuccess = false">
        <div class="success-dialog">
          <div class="success-icon">✓</div>
          <h3>支付成功</h3>
          <p>您的订单已支付成功</p>
          <button class="btn btn-primary" @click="showSuccess = false">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useToast } from '../composables/useToast'
import api from '../api'

const toast = useToast()
const orders = ref([])
const loading = ref(true)
const showConfirm = ref(false)
const showPayment = ref(false)
const showSuccess = ref(false)
const pendingOrder = ref(null)
const currentOrder = ref(null)
const selectedMethod = ref('WECHAT')
const paying = ref(false)
const paymentMode = ref('mock')

const statusText = {
  PENDING: '待付款',
  PAID: '已付款',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleString('zh-CN')
}

const fetchOrders = async () => {
  try {
    const res = await api.get('/orders')
    if (res.code === 200) {
      orders.value = res.data.content
    }
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}

const fetchPaymentConfig = async () => {
  try {
    const res = await api.get('/payment/config')
    if (res.code === 200) {
      paymentMode.value = res.data.mode
    }
  } catch (e) {
    console.error(e)
  }
}

const cancelOrder = (order) => {
  pendingOrder.value = order
  showConfirm.value = true
}

const confirmCancel = async () => {
  if (!pendingOrder.value) return
  try {
    await api.put(`/orders/${pendingOrder.value.id}/cancel`)
    toast.success('订单已取消')
    fetchOrders()
  } catch (e) {
    toast.error('取消失败')
  }
  showConfirm.value = false
  pendingOrder.value = null
}

const payOrder = (order) => {
  currentOrder.value = order
  selectedMethod.value = 'WECHAT'
  showPayment.value = true
}

const confirmPayment = async () => {
  if (!currentOrder.value) return
  paying.value = true
  
  try {
    // 创建支付
    const createRes = await api.post('/payment/create', {
      orderId: currentOrder.value.id,
      paymentMethod: selectedMethod.value
    })
    
    if (createRes.code !== 200) {
      toast.error(createRes.message || '创建支付失败')
      paying.value = false
      return
    }
    
    // 模拟支付确认
    const confirmRes = await api.post('/payment/confirm', {
      orderId: currentOrder.value.id,
      paymentMethod: selectedMethod.value
    })
    
    if (confirmRes.code === 200) {
      showPayment.value = false
      showSuccess.value = true
      fetchOrders()
    } else {
      toast.error(confirmRes.message || '支付失败')
    }
  } catch (e) {
    toast.error('支付失败，请重试')
  }
  
  paying.value = false
}

onMounted(() => {
  fetchOrders()
  fetchPaymentConfig()
})
</script>

<style scoped>
.orders-page {
  padding: 40px 0;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--gray-800);
  margin-bottom: 32px;
}

.order-card {
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  margin-bottom: 20px;
  overflow: hidden;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: var(--gray-50);
  border-bottom: 1px solid var(--gray-100);
}

.order-info {
  display: flex;
  gap: 24px;
}

.order-no {
  font-weight: 500;
  color: var(--gray-700);
}

.order-time {
  color: var(--gray-500);
  font-size: 14px;
}

.order-status {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
}

.order-status.pending { background: #fef3c7; color: #d97706; }
.order-status.paid { background: #dbeafe; color: #2563eb; }
.order-status.shipped { background: #e0e7ff; color: #4f46e5; }
.order-status.completed { background: #d1fae5; color: #059669; }
.order-status.cancelled { background: #f3f4f6; color: #6b7280; }

.order-body {
  padding: 24px;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--gray-100);
}

.order-item:last-child {
  border-bottom: none;
}

.order-item img {
  width: 60px;
  height: 80px;
  object-fit: cover;
  border-radius: var(--radius-sm);
}

.order-item .item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-title {
  font-weight: 500;
  color: var(--gray-800);
}

.item-qty {
  font-size: 14px;
  color: var(--gray-500);
}

.item-price {
  font-weight: 500;
  color: var(--gray-700);
}

.tracking-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  margin-top: 12px;
  background: #eff6ff;
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.tracking-label {
  color: var(--gray-500);
  white-space: nowrap;
}

.tracking-no {
  color: var(--primary);
  font-weight: 600;
  font-family: monospace;
  letter-spacing: 0.5px;
}

.order-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--gray-100);
}

.total-amount strong {
  font-size: 18px;
  color: var(--accent);
}

.order-footer {
  padding: 16px 24px;
  background: var(--gray-50);
  border-top: 1px solid var(--gray-100);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
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

.confirm-dialog {
  background: white;
  border-radius: var(--radius);
  padding: 32px;
  text-align: center;
  max-width: 360px;
  width: 90%;
}

.confirm-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.confirm-dialog h3 {
  font-size: 20px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 8px;
}

.confirm-dialog p {
  color: var(--gray-600);
  margin-bottom: 24px;
}

.confirm-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.btn-danger {
  background: var(--danger);
  color: white;
}

.btn-danger:hover {
  background: #dc2626;
}

/* 支付弹窗样式 */
.payment-dialog {
  background: white;
  border-radius: var(--radius-lg);
  width: 90%;
  max-width: 420px;
  overflow: hidden;
}

.payment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--gray-100);
}

.payment-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--gray-800);
}

.close-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--gray-100);
  color: var(--gray-500);
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: var(--gray-200);
}

.payment-amount {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid var(--gray-100);
}

.payment-amount span {
  font-size: 14px;
  color: var(--gray-500);
}

.payment-amount strong {
  font-size: 32px;
  font-weight: 700;
  color: var(--accent);
}

.payment-methods {
  padding: 20px 24px;
}

.payment-method {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 2px solid var(--gray-100);
  border-radius: var(--radius);
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.payment-method:hover {
  border-color: var(--gray-200);
}

.payment-method.active {
  border-color: #10b981;
  background: #ecfdf5;
}

.method-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.method-icon.wechat {
  background: #e8f8ee;
}

.method-icon.alipay {
  background: #e6f0ff;
}

.method-info {
  flex: 1;
}

.method-name {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 2px;
}

.method-desc {
  font-size: 13px;
  color: var(--gray-500);
}

.method-check {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #10b981;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.mock-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 24px 16px;
  padding: 12px 16px;
  background: #fef3c7;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: #92400e;
}

.notice-icon {
  font-size: 16px;
}

.payment-actions {
  display: flex;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid var(--gray-100);
}

.payment-actions .btn {
  flex: 1;
}

.btn-pay {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.btn-pay:hover {
  background: linear-gradient(135deg, #059669 0%, #047857 100%);
}

/* 支付成功弹窗 */
.success-dialog {
  background: white;
  border-radius: var(--radius-lg);
  padding: 40px;
  text-align: center;
  max-width: 360px;
  width: 90%;
}

.success-icon {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: white;
  font-size: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
}

.success-dialog h3 {
  font-size: 24px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 8px;
}

.success-dialog p {
  color: var(--gray-500);
  margin-bottom: 24px;
}

.success-dialog .btn {
  min-width: 120px;
}
</style>
