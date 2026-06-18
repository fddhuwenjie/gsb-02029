<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <h1>欢迎回来</h1>
        <p>登录你的账号继续探索</p>
      </div>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label>邮箱</label>
          <input type="email" v-model="form.email" class="input" placeholder="请输入邮箱" required />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input type="password" v-model="form.password" class="input" placeholder="请输入密码" required />
        </div>
        <button type="submit" class="btn btn-primary btn-lg" style="width: 100%" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
      <p class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useToast } from '../composables/useToast'
import api from '../api'

const router = useRouter()
const userStore = useUserStore()
const toast = useToast()
const loading = ref(false)
const form = ref({ email: '', password: '' })

const handleLogin = async () => {
  if (!form.value.email || !form.value.password) {
    toast.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const res = await api.post('/auth/login', form.value)
    if (res.code === 200) {
      userStore.setUser(res.data)
      toast.success('登录成功')
      setTimeout(() => router.push('/'), 1000)
    } else {
      toast.error(res.message || '登录失败')
    }
  } catch (e) {
    toast.error('登录失败，请检查账号密码')
  }
  loading.value = false
}
</script>

<style scoped>
.auth-page {
  min-height: calc(100vh - 80px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
}

.auth-card {
  width: 100%;
  max-width: 420px;
  background: white;
  border-radius: var(--radius-lg);
  padding: 48px;
  box-shadow: var(--shadow-xl);
}

.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

.auth-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--gray-800);
  margin-bottom: 8px;
}

.auth-header p {
  color: var(--gray-500);
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

.auth-footer {
  text-align: center;
  margin-top: 24px;
  color: var(--gray-500);
}

.auth-footer a {
  color: var(--primary);
  font-weight: 500;
}
</style>
