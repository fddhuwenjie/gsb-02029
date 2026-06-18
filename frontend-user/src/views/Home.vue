<template>
  <div class="home">
    <!-- Hero Section -->
    <section class="hero">
      <div class="container">
        <div class="hero-content">
          <h1>发现你的下一本<span class="highlight">好书</span></h1>
          <p>在这里，每一本书都有故事。以优惠的价格，找到你心仪的二手书籍。</p>
          <div class="hero-actions">
            <router-link to="/books" class="btn btn-primary btn-lg">开始探索</router-link>
            <router-link to="/publish" class="btn btn-secondary btn-lg">发布书籍</router-link>
          </div>
          <div class="hero-stats">
            <div class="stat">
              <span class="stat-value">1000+</span>
              <span class="stat-label">在售书籍</span>
            </div>
            <div class="stat">
              <span class="stat-value">500+</span>
              <span class="stat-label">活跃用户</span>
            </div>
            <div class="stat">
              <span class="stat-value">98%</span>
              <span class="stat-label">好评率</span>
            </div>
          </div>
        </div>
        <div class="hero-visual">
          <div class="book-stack">
            <div class="floating-book b1"></div>
            <div class="floating-book b2"></div>
            <div class="floating-book b3"></div>
          </div>
        </div>
      </div>
    </section>

    <!-- Categories -->
    <section class="categories">
      <div class="container">
        <h2 class="section-title">热门分类</h2>
        <div class="category-grid">
          <router-link 
            v-for="cat in categories" 
            :key="cat.id" 
            :to="`/books?category=${cat.id}`"
            class="category-card"
          >
            <div class="category-icon" :class="getCategoryClass(cat.icon)">
              <component :is="getCategoryIcon(cat.icon)" />
            </div>
            <span>{{ cat.name }}</span>
          </router-link>
        </div>
      </div>
    </section>

    <!-- Latest Books -->
    <section class="latest-books">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">最新上架</h2>
          <router-link to="/books" class="view-all">查看全部 →</router-link>
        </div>
        <div class="books-grid" v-if="books.length">
          <BookCard v-for="book in books" :key="book.id" :book="book" />
        </div>
        <div class="loading" v-else-if="loading">
          <div class="spinner"></div>
        </div>
      </div>
    </section>

    <!-- Features -->
    <section class="features">
      <div class="container">
        <h2 class="section-title">为什么选择我们</h2>
        <div class="features-grid">
          <div class="feature-card">
            <div class="feature-icon">🔒</div>
            <h3>安全交易</h3>
            <p>平台担保交易，保障买卖双方权益</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon">💰</div>
            <h3>超值价格</h3>
            <p>二手书籍低至原价3折起</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon">📦</div>
            <h3>品质保证</h3>
            <p>严格审核，确保书籍品质</p>
          </div>
          <div class="feature-card">
            <div class="feature-icon">🚀</div>
            <h3>快速发货</h3>
            <p>下单后48小时内发货</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, h } from 'vue'
import api from '../api'
import BookCard from '../components/BookCard.vue'

const categories = ref([])
const books = ref([])
const loading = ref(true)

// 分类图标映射
const iconMap = {
  'book': h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
    h('path', { d: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20' }),
    h('path', { d: 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z' })
  ]),
  'graduation-cap': h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
    h('path', { d: 'M22 10v6M2 10l10-5 10 5-10 5z' }),
    h('path', { d: 'M6 12v5c3 3 9 3 12 0v-5' })
  ]),
  'laptop': h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
    h('rect', { x: '2', y: '3', width: '20', height: '14', rx: '2' }),
    h('path', { d: 'M2 20h20' })
  ]),
  'chart-line': h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
    h('path', { d: 'M3 3v18h18' }),
    h('path', { d: 'M18 9l-5 5-4-4-6 6' })
  ]),
  'users': h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
    h('path', { d: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2' }),
    h('circle', { cx: '9', cy: '7', r: '4' }),
    h('path', { d: 'M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75' })
  ]),
  'globe': h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
    h('circle', { cx: '12', cy: '12', r: '10' }),
    h('path', { d: 'M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z' })
  ]),
  'edit': h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
    h('path', { d: 'M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7' }),
    h('path', { d: 'M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z' })
  ]),
  'coffee': h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
    h('path', { d: 'M18 8h1a4 4 0 0 1 0 8h-1M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z' }),
    h('path', { d: 'M6 1v3M10 1v3M14 1v3' })
  ])
}

const colorMap = {
  'book': 'icon-blue',
  'graduation-cap': 'icon-green',
  'laptop': 'icon-purple',
  'chart-line': 'icon-orange',
  'users': 'icon-pink',
  'globe': 'icon-cyan',
  'edit': 'icon-yellow',
  'coffee': 'icon-red'
}

const getCategoryIcon = (icon) => iconMap[icon] || iconMap['book']
const getCategoryClass = (icon) => colorMap[icon] || 'icon-blue'

onMounted(async () => {
  try {
    const [catRes, bookRes] = await Promise.all([
      api.get('/categories'),
      api.get('/books', { params: { size: 8 } })
    ])
    if (catRes.code === 200) categories.value = catRes.data
    if (bookRes.code === 200) books.value = bookRes.data.content
  } catch (e) {
    console.error(e)
  }
  loading.value = false
})
</script>

<style scoped>
.hero {
  padding: 80px 0 120px;
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 50%, #bbf7d0 100%);
  overflow: hidden;
}

.hero .container {
  display: flex;
  align-items: center;
  gap: 80px;
}

.hero-content {
  flex: 1;
}

.hero h1 {
  font-size: 56px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--gray-900);
  margin-bottom: 24px;
}

.hero h1 .highlight {
  background: linear-gradient(135deg, var(--primary), var(--accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero p {
  font-size: 18px;
  color: var(--gray-600);
  margin-bottom: 32px;
  max-width: 480px;
}

.hero-actions {
  display: flex;
  gap: 16px;
  margin-bottom: 48px;
}

.hero-stats {
  display: flex;
  gap: 48px;
}

.stat {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--gray-800);
}

.stat-label {
  font-size: 14px;
  color: var(--gray-500);
}

.hero-visual {
  flex: 1;
  display: flex;
  justify-content: center;
}

.book-stack {
  position: relative;
  width: 300px;
  height: 400px;
}

.floating-book {
  position: absolute;
  width: 180px;
  height: 260px;
  border-radius: 8px;
  box-shadow: var(--shadow-xl);
}

.floating-book.b1 {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  top: 0;
  left: 0;
  transform: rotate(-10deg);
  animation: float1 3s ease-in-out infinite;
}

.floating-book.b2 {
  background: linear-gradient(135deg, #f97316, #fb923c);
  top: 40px;
  left: 60px;
  transform: rotate(5deg);
  animation: float2 3s ease-in-out infinite 0.5s;
}

.floating-book.b3 {
  background: linear-gradient(135deg, #10b981, #34d399);
  top: 80px;
  left: 120px;
  transform: rotate(-5deg);
  animation: float3 3s ease-in-out infinite 1s;
}

@keyframes float1 {
  0%, 100% { transform: rotate(-10deg) translateY(0); }
  50% { transform: rotate(-10deg) translateY(-15px); }
}

@keyframes float2 {
  0%, 100% { transform: rotate(5deg) translateY(0); }
  50% { transform: rotate(5deg) translateY(-20px); }
}

@keyframes float3 {
  0%, 100% { transform: rotate(-5deg) translateY(0); }
  50% { transform: rotate(-5deg) translateY(-10px); }
}

.categories {
  padding: 80px 0;
}

.section-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--gray-800);
  margin-bottom: 40px;
  text-align: center;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.category-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px 24px;
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  transition: all 0.3s;
  font-weight: 500;
  color: var(--gray-700);
}

.category-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  color: var(--primary);
}

.category-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.category-icon svg {
  width: 28px;
  height: 28px;
}

.category-icon.icon-blue {
  background: #eff6ff;
  color: #3b82f6;
}

.category-icon.icon-green {
  background: #f0fdf4;
  color: #22c55e;
}

.category-icon.icon-purple {
  background: #faf5ff;
  color: #a855f7;
}

.category-icon.icon-orange {
  background: #fff7ed;
  color: #f97316;
}

.category-icon.icon-pink {
  background: #fdf2f8;
  color: #ec4899;
}

.category-icon.icon-cyan {
  background: #ecfeff;
  color: #06b6d4;
}

.category-icon.icon-yellow {
  background: #fefce8;
  color: #eab308;
}

.category-icon.icon-red {
  background: #fef2f2;
  color: #ef4444;
}

.latest-books {
  padding: 80px 0;
  background: var(--gray-50);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 40px;
}

.section-header .section-title {
  margin-bottom: 0;
  text-align: left;
}

.view-all {
  color: var(--primary);
  font-weight: 500;
}

.books-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.features {
  padding: 80px 0;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.feature-card {
  text-align: center;
  padding: 40px 24px;
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.feature-icon {
  font-size: 48px;
  margin-bottom: 20px;
}

.feature-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 8px;
}

.feature-card p {
  font-size: 14px;
  color: var(--gray-500);
}
</style>
