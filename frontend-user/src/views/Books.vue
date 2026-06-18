<template>
  <div class="books-page">
    <div class="container">
      <div class="page-header">
        <h1>浏览书籍</h1>
        <div class="search-bar">
          <input 
            type="text" 
            v-model="keyword" 
            class="input" 
            placeholder="搜索书名或作者..."
            @keyup.enter="search"
          />
          <button class="btn btn-primary" @click="search">搜索</button>
        </div>
      </div>

      <div class="content-wrapper">
        <aside class="sidebar">
          <h3>分类筛选</h3>
          <ul class="category-list">
            <li 
              :class="{ active: !selectedCategory }"
              @click="selectCategory(null)"
            >
              全部分类
            </li>
            <li 
              v-for="cat in categories" 
              :key="cat.id"
              :class="{ active: selectedCategory === cat.id }"
              @click="selectCategory(cat.id)"
            >
              {{ cat.name }}
            </li>
          </ul>
        </aside>

        <main class="main-content">
          <div class="books-grid" v-if="books.length">
            <BookCard v-for="book in books" :key="book.id" :book="book" />
          </div>
          <div class="loading" v-else-if="loading">
            <div class="spinner"></div>
          </div>
          <div class="empty" v-else>
            <p>暂无书籍</p>
          </div>

          <div class="pagination" v-if="totalPages > 1">
            <button 
              class="btn btn-secondary" 
              :disabled="page === 0"
              @click="changePage(page - 1)"
            >
              上一页
            </button>
            <span class="page-info">{{ page + 1 }} / {{ totalPages }}</span>
            <button 
              class="btn btn-secondary" 
              :disabled="page >= totalPages - 1"
              @click="changePage(page + 1)"
            >
              下一页
            </button>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../api'
import BookCard from '../components/BookCard.vue'

const route = useRoute()
const router = useRouter()

const categories = ref([])
const books = ref([])
const loading = ref(true)
const keyword = ref('')
const selectedCategory = ref(null)
const page = ref(0)
const totalPages = ref(0)

const fetchCategories = async () => {
  try {
    const res = await api.get('/categories')
    if (res.code === 200) categories.value = res.data
  } catch (e) {
    console.error(e)
  }
}

const fetchBooks = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: 12 }
    if (selectedCategory.value) params.categoryId = selectedCategory.value
    if (keyword.value) params.keyword = keyword.value
    
    const res = await api.get('/books', { params })
    if (res.code === 200) {
      books.value = res.data.content
      totalPages.value = res.data.totalPages
    }
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}

const selectCategory = (id) => {
  selectedCategory.value = id
  page.value = 0
  fetchBooks()
}

const search = () => {
  page.value = 0
  fetchBooks()
}

const changePage = (p) => {
  page.value = p
  fetchBooks()
}

onMounted(() => {
  if (route.query.category) {
    selectedCategory.value = Number(route.query.category)
  }
  fetchCategories()
  fetchBooks()
})
</script>

<style scoped>
.books-page {
  padding: 40px 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 40px;
}

.page-header h1 {
  font-size: 32px;
  font-weight: 700;
  color: var(--gray-800);
}

.search-bar {
  display: flex;
  gap: 12px;
}

.search-bar .input {
  width: 300px;
}

.content-wrapper {
  display: flex;
  gap: 40px;
}

.sidebar {
  width: 220px;
  flex-shrink: 0;
}

.sidebar h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 16px;
}

.category-list {
  list-style: none;
}

.category-list li {
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--gray-600);
  transition: all 0.2s;
  margin-bottom: 4px;
}

.category-list li:hover {
  background: var(--gray-100);
}

.category-list li.active {
  background: var(--primary);
  color: white;
}

.main-content {
  flex: 1;
}

.books-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 40px;
}

.page-info {
  color: var(--gray-600);
}
</style>
