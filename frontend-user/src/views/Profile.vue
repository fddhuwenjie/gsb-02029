<template>
  <div class="profile-page">
    <div class="container">
      <div class="profile-header">
        <div class="avatar">{{ userStore.userInfo?.nickname?.[0] || 'U' }}</div>
        <div class="user-info">
          <h1>{{ userStore.userInfo?.nickname }}</h1>
          <p>{{ userStore.userInfo?.email }}</p>
        </div>
      </div>

      <div class="profile-content">
        <div class="profile-nav">
          <button 
            :class="{ active: activeTab === 'favorites' }"
            @click="activeTab = 'favorites'"
          >
            我的收藏
          </button>
          <button 
            :class="{ active: activeTab === 'mybooks' }"
            @click="activeTab = 'mybooks'"
          >
            我发布的
          </button>
        </div>

        <div class="tab-content">
          <!-- Favorites -->
          <div v-if="activeTab === 'favorites'">
            <div class="books-grid" v-if="favorites.length">
              <div class="book-item" v-for="fav in favorites" :key="fav.id">
                <router-link :to="`/book/${fav.book?.id}`" class="book-link">
                  <img :src="fav.book?.coverImage || 'https://via.placeholder.com/100x140'" />
                  <div class="book-info">
                    <h3>{{ fav.book?.title }}</h3>
                    <p>{{ fav.book?.author }}</p>
                    <span class="price">¥{{ fav.book?.price }}</span>
                  </div>
                </router-link>
                <button class="remove-btn" @click="removeFavorite(fav.book?.id)">移除</button>
              </div>
            </div>
            <div class="empty" v-else>
              <p>暂无收藏</p>
            </div>
          </div>

          <!-- My Books -->
          <div v-if="activeTab === 'mybooks'">
            <div class="books-grid" v-if="myBooks.length">
              <div class="book-item" v-for="book in myBooks" :key="book.id">
                <router-link :to="`/book/${book.id}`" class="book-link">
                  <img :src="book.coverImage || 'https://via.placeholder.com/100x140'" />
                  <div class="book-info">
                    <h3>{{ book.title }}</h3>
                    <p>{{ book.author }}</p>
                    <span class="price">¥{{ book.price }}</span>
                  </div>
                </router-link>
              </div>
            </div>
            <div class="empty" v-else>
              <p>暂无发布的书籍</p>
              <router-link to="/publish" class="btn btn-primary" style="margin-top: 16px">去发布</router-link>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useUserStore } from '../stores/user'
import { useToast } from '../composables/useToast'
import api from '../api'

const userStore = useUserStore()
const toast = useToast()
const activeTab = ref('favorites')
const favorites = ref([])
const myBooks = ref([])

const fetchFavorites = async () => {
  try {
    const res = await api.get('/favorites')
    if (res.code === 200) favorites.value = res.data
  } catch (e) {
    console.error(e)
  }
}

const fetchMyBooks = async () => {
  try {
    const res = await api.get('/books/my')
    if (res.code === 200) myBooks.value = res.data.content
  } catch (e) {
    console.error(e)
  }
}

const removeFavorite = async (bookId) => {
  try {
    await api.delete(`/favorites/${bookId}`)
    toast.success('已取消收藏')
    fetchFavorites()
  } catch (e) {
    toast.error('操作失败')
  }
}

watch(activeTab, (tab) => {
  if (tab === 'favorites') fetchFavorites()
  else if (tab === 'mybooks') fetchMyBooks()
})

onMounted(fetchFavorites)
</script>

<style scoped>
.profile-page {
  padding: 40px 0;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 48px;
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
}

.user-info h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--gray-800);
  margin-bottom: 4px;
}

.user-info p {
  color: var(--gray-500);
}

.profile-nav {
  display: flex;
  gap: 8px;
  margin-bottom: 32px;
}

.profile-nav button {
  padding: 12px 24px;
  border-radius: var(--radius-sm);
  background: white;
  color: var(--gray-600);
  font-weight: 500;
  transition: all 0.2s;
}

.profile-nav button:hover {
  background: var(--gray-100);
}

.profile-nav button.active {
  background: var(--primary);
  color: white;
}

.books-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.book-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.book-link {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}

.book-item img {
  width: 80px;
  height: 110px;
  object-fit: cover;
  border-radius: var(--radius-sm);
}

.book-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 4px;
}

.book-info p {
  font-size: 14px;
  color: var(--gray-500);
  margin-bottom: 8px;
}

.book-info .price {
  font-weight: 600;
  color: var(--accent);
}

.remove-btn {
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  background: var(--gray-100);
  color: var(--gray-600);
  font-size: 14px;
}

.remove-btn:hover {
  background: var(--danger);
  color: white;
}
</style>
