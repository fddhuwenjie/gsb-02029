<template>
  <div>
  <header class="header">
    <div class="container header-inner">
      <router-link to="/" class="logo">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
          <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
        </svg>
        <span>二手书市场</span>
      </router-link>
      
      <nav class="nav">
        <router-link to="/books" class="nav-link">浏览书籍</router-link>
        <router-link to="/publish" class="nav-link" v-if="userStore.isLoggedIn">发布书籍</router-link>
      </nav>
      
      <div class="header-actions">
        <template v-if="userStore.isLoggedIn">
          <router-link to="/cart" class="cart-btn">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="9" cy="21" r="1"></circle>
              <circle cx="20" cy="21" r="1"></circle>
              <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
            </svg>
            <span v-if="cartStore.count" class="cart-badge">{{ cartStore.count }}</span>
          </router-link>
          <div class="user-menu" @click="showMenu = !showMenu" v-click-outside="() => showMenu = false">
            <div class="avatar">{{ userStore.userInfo?.nickname?.[0] || 'U' }}</div>
            <div class="dropdown" v-if="showMenu">
              <router-link to="/profile" class="dropdown-item">个人中心</router-link>
              <router-link to="/orders" class="dropdown-item">我的订单</router-link>
              <div class="dropdown-divider"></div>
              <div class="dropdown-item" @click="handleLogout">退出登录</div>
            </div>
          </div>
        </template>
        <template v-else>
          <button class="btn btn-secondary" @click="openLogin">登录</button>
          <button class="btn btn-primary" @click="openRegister">注册</button>
        </template>
      </div>
    </div>
  </header>

  <!-- 登录弹窗 -->
  <div v-if="authModalVisible" class="auth-overlay" @click.self="authModalVisible = false">
    <div class="auth-modal">
      <button class="auth-close" @click="authModalVisible = false">&times;</button>
      
      <!-- 登录表单 -->
      <template v-if="authMode === 'login'">
        <div class="auth-header">
          <h2>欢迎回来</h2>
          <p>登录你的账号继续探索</p>
        </div>
        <form @submit.prevent="handleLogin">
          <div class="form-group">
            <label>邮箱</label>
            <input type="email" v-model="loginForm.email" class="input" placeholder="请输入邮箱" required />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input type="password" v-model="loginForm.password" class="input" placeholder="请输入密码" required />
          </div>
          <button type="submit" class="btn btn-primary btn-lg" style="width: 100%" :disabled="authLoading">
            {{ authLoading ? '登录中...' : '登录' }}
          </button>
        </form>
        <p class="auth-footer">
          还没有账号？<a href="#" @click.prevent="authMode = 'register'">立即注册</a>
        </p>
      </template>

      <!-- 注册表单 -->
      <template v-else>
        <div class="auth-header">
          <h2>创建账号</h2>
          <p>加入我们，开始你的阅读之旅</p>
        </div>
        <form @submit.prevent="handleRegister">
          <div class="form-group">
            <label>昵称</label>
            <input type="text" v-model="registerForm.nickname" class="input" placeholder="请输入昵称" required />
          </div>
          <div class="form-group">
            <label>邮箱</label>
            <input type="email" v-model="registerForm.email" class="input" placeholder="请输入邮箱" required />
          </div>
          <div class="form-group">
            <label>密码</label>
            <input type="password" v-model="registerForm.password" class="input" placeholder="请输入密码（至少6位）" required minlength="6" />
          </div>
          <button type="submit" class="btn btn-primary btn-lg" style="width: 100%" :disabled="authLoading">
            {{ authLoading ? '注册中...' : '注册' }}
          </button>
        </form>
        <p class="auth-footer">
          已有账号？<a href="#" @click.prevent="authMode = 'login'">立即登录</a>
        </p>
      </template>
    </div>
  </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useCartStore } from '../stores/cart'
import { useToast } from '../composables/useToast'
import api from '../api'

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const toast = useToast()
const showMenu = ref(false)

const authModalVisible = ref(false)
const authMode = ref('login')
const authLoading = ref(false)
const loginForm = ref({ email: '', password: '' })
const registerForm = ref({ nickname: '', email: '', password: '' })

const vClickOutside = {
  mounted(el, binding) {
    el._clickOutside = (e) => {
      if (!el.contains(e.target)) binding.value()
    }
    document.addEventListener('click', el._clickOutside)
  },
  unmounted(el) {
    document.removeEventListener('click', el._clickOutside)
  }
}

const openLogin = () => {
  authMode.value = 'login'
  loginForm.value = { email: '', password: '' }
  authModalVisible.value = true
}

const openRegister = () => {
  authMode.value = 'register'
  registerForm.value = { nickname: '', email: '', password: '' }
  authModalVisible.value = true
}

const handleLogin = async () => {
  if (!loginForm.value.email || !loginForm.value.password) {
    toast.warning('请输入账号和密码')
    return
  }
  authLoading.value = true
  try {
    const res = await api.post('/auth/login', loginForm.value)
    if (res.code === 200) {
      userStore.setUser(res.data)
      toast.success('登录成功')
      authModalVisible.value = false
      cartStore.fetchCart()
    } else {
      toast.error(res.message || '登录失败')
    }
  } catch (e) {
    toast.error('登录失败，请检查账号密码')
  }
  authLoading.value = false
}

const handleRegister = async () => {
  authLoading.value = true
  try {
    const res = await api.post('/auth/register', registerForm.value)
    if (res.code === 200) {
      userStore.setUser(res.data)
      toast.success('注册成功')
      authModalVisible.value = false
      cartStore.fetchCart()
    } else {
      toast.error(res.message || '注册失败')
    }
  } catch (e) {
    toast.error('注册失败')
  }
  authLoading.value = false
}

const handleLogout = () => {
  userStore.logout()
  router.push('/')
}

const showLoginModal = () => {
  openLogin()
}

defineExpose({ showLoginModal, openLogin, openRegister })

onMounted(() => {
  if (userStore.isLoggedIn) {
    cartStore.fetchCart()
  }

  window.__showLoginModal = showLoginModal
})

onBeforeUnmount(() => {
  delete window.__showLoginModal
})
</script>

<style scoped>
.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 80px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--gray-100);
  z-index: 100;
}

.header-inner {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 700;
  color: var(--primary);
}

.nav {
  display: flex;
  gap: 32px;
}

.nav-link {
  font-weight: 500;
  color: var(--gray-600);
  transition: color 0.2s;
}

.nav-link:hover,
.nav-link.router-link-active {
  color: var(--primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.cart-btn {
  position: relative;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--gray-100);
  color: var(--gray-700);
  transition: all 0.2s;
}

.cart-btn:hover {
  background: var(--gray-200);
}

.cart-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  background: var(--accent);
  color: white;
  font-size: 12px;
  font-weight: 600;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-menu {
  position: relative;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  cursor: pointer;
}

.dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 160px;
  background: white;
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-lg);
  padding: 8px 0;
  z-index: 10;
}

.dropdown-item {
  display: block;
  padding: 10px 16px;
  color: var(--gray-700);
  transition: background 0.2s;
  cursor: pointer;
}

.dropdown-item:hover {
  background: var(--gray-50);
}

.dropdown-divider {
  height: 1px;
  background: var(--gray-100);
  margin: 8px 0;
}

/* Auth Modal */
.auth-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.auth-modal {
  width: 100%;
  max-width: 420px;
  background: white;
  border-radius: var(--radius-lg);
  padding: 48px;
  box-shadow: var(--shadow-xl);
  position: relative;
  animation: slideUp 0.3s ease;
}

.auth-close {
  position: absolute;
  top: 16px;
  right: 20px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gray-100);
  border: none;
  border-radius: 50%;
  font-size: 20px;
  color: var(--gray-500);
  cursor: pointer;
  transition: all 0.2s;
}

.auth-close:hover {
  background: var(--gray-200);
  color: var(--gray-700);
}

.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

.auth-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: var(--gray-800);
  margin-bottom: 8px;
}

.auth-header p {
  color: var(--gray-500);
}

.auth-modal .form-group {
  margin-bottom: 20px;
}

.auth-modal .form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-700);
  margin-bottom: 8px;
}

.auth-footer {
  text-align: center;
  margin-top: 24px;
  color: var(--gray-500);
}

.auth-footer a {
  color: var(--primary);
  font-weight: 500;
  cursor: pointer;
}

.auth-footer a:hover {
  text-decoration: underline;
}
</style>
