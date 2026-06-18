<template>
  <div class="book-detail" v-if="book">
    <div class="container">
      <div class="detail-wrapper">
        <div class="book-cover">
          <img :src="book.coverImage || 'https://via.placeholder.com/400x560?text=No+Cover'" :alt="book.title" />
        </div>
        
        <div class="book-info">
          <div class="book-meta">
            <span class="badge badge-primary">{{ book.quality }}</span>
            <span class="stock" v-if="book.stock > 0">库存: {{ book.stock }}</span>
            <span class="stock out" v-else>已售罄</span>
          </div>
          
          <h1 class="book-title">{{ book.title }}</h1>
          <p class="book-author">{{ book.author }}</p>
          
          <div class="price-section">
            <span class="current-price">¥{{ book.price }}</span>
            <span class="original-price" v-if="book.originalPrice">原价 ¥{{ book.originalPrice }}</span>
            <span class="discount" v-if="book.originalPrice">
              省 ¥{{ (book.originalPrice - book.price).toFixed(2) }}
            </span>
          </div>
          
          <div class="info-list">
            <div class="info-item" v-if="book.publisher">
              <span class="label">出版社</span>
              <span class="value">{{ book.publisher }}</span>
            </div>
            <div class="info-item" v-if="book.isbn">
              <span class="label">ISBN</span>
              <span class="value">{{ book.isbn }}</span>
            </div>
          </div>
          
          <div class="actions">
            <button 
              class="btn btn-primary btn-lg" 
              @click="addToCart"
              :disabled="book.stock === 0"
            >
              加入购物车
            </button>
            <button class="btn btn-secondary btn-lg" @click="toggleFavorite">
              {{ isFavorite ? '❤️ 已收藏' : '🤍 收藏' }}
            </button>
          </div>
        </div>
      </div>
      
      <div class="description-section" v-if="book.description">
        <h2>书籍简介</h2>
        <p>{{ book.description }}</p>
      </div>
    </div>
  </div>
  <div class="loading" v-else>
    <div class="spinner"></div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useCartStore } from '../stores/cart'
import { useToast } from '../composables/useToast'
import api from '../api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const toast = useToast()

const book = ref(null)
const isFavorite = ref(false)

const fetchBook = async () => {
  try {
    const res = await api.get(`/books/${route.params.id}`)
    if (res.code === 200) {
      book.value = res.data
    }
  } catch (e) {
    console.error(e)
  }
}

const checkFavorite = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const res = await api.get(`/favorites/check/${route.params.id}`)
    if (res.code === 200) {
      isFavorite.value = res.data
    }
  } catch (e) {
    console.error(e)
  }
}

const addToCart = async () => {
  if (!userStore.isLoggedIn) {
    toast.warning('请先登录')
    router.push('/login')
    return
  }
  const success = await cartStore.addToCart(book.value.id)
  if (success) {
    toast.success('已添加到购物车')
  }
}

const toggleFavorite = async () => {
  if (!userStore.isLoggedIn) {
    toast.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    if (isFavorite.value) {
      await api.delete(`/favorites/${book.value.id}`)
      toast.success('已取消收藏')
    } else {
      await api.post(`/favorites/${book.value.id}`)
      toast.success('收藏成功')
    }
    isFavorite.value = !isFavorite.value
  } catch (e) {
    toast.error('操作失败')
  }
}

onMounted(() => {
  fetchBook()
  checkFavorite()
})
</script>

<style scoped>
.book-detail {
  padding: 40px 0;
}

.detail-wrapper {
  display: flex;
  gap: 64px;
  margin-bottom: 64px;
}

.book-cover {
  width: 400px;
  flex-shrink: 0;
}

.book-cover img {
  width: 100%;
  border-radius: var(--radius);
  box-shadow: var(--shadow-lg);
}

.book-info {
  flex: 1;
}

.book-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.stock {
  font-size: 14px;
  color: var(--success);
}

.stock.out {
  color: var(--danger);
}

.book-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--gray-800);
  margin-bottom: 8px;
}

.book-author {
  font-size: 18px;
  color: var(--gray-500);
  margin-bottom: 24px;
}

.price-section {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 32px;
  padding: 24px;
  background: var(--gray-50);
  border-radius: var(--radius);
}

.current-price {
  font-size: 36px;
  font-weight: 700;
  color: var(--accent);
}

.original-price {
  font-size: 16px;
  color: var(--gray-400);
  text-decoration: line-through;
}

.discount {
  padding: 4px 12px;
  background: rgba(249, 115, 22, 0.1);
  color: var(--accent);
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

.info-list {
  margin-bottom: 32px;
}

.info-item {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid var(--gray-100);
}

.info-item .label {
  width: 80px;
  color: var(--gray-500);
}

.info-item .value {
  color: var(--gray-700);
}

.actions {
  display: flex;
  gap: 16px;
}

.description-section {
  background: white;
  padding: 32px;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.description-section h2 {
  font-size: 20px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 16px;
}

.description-section p {
  color: var(--gray-600);
  line-height: 1.8;
}
</style>
