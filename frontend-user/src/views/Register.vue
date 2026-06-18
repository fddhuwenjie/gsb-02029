<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <h1>创建账号</h1>
        <p>加入我们，开始你的阅读之旅</p>
      </div>
      <form @submit.prevent="handleRegister">
        <div class="form-group">
          <label>昵称</label>
          <input type="text" v-model="form.nickname" class="input" placeholder="请输入昵称" required />
        </div>
        <div class="form-group">
          <label>邮箱</label>
          <input type="email" v-model="form.email" class="input" placeholder="请输入邮箱" required />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input type="password" v-model="form.password" class="input" placeholder="请输入密码（至少6位）" required minlength="6" />
        </div>
        <button type="submit" class="btn btn-primary btn-lg" style="width: 100%" :disabled="loading">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>
      <p class="auth-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
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
const form = ref({ nickname: '', email: '', password: '' })

const handleRegister = async () => {
  loading.value = true
  try {
    const res = await api.post('/auth/register', form.value)
    if (res.code === 200) {
      userStore.setUser(res.data)
      toast.success('注册成功')
      setTimeout(() => router.push('/'), 1000)
    } else {
      toast.error(res.message || '注册失败')
    }
  } catch (e) {
    toast.error('注册失败')
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
