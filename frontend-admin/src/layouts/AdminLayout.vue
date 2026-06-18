<template>
  <el-container class="layout-container">
    <el-aside width="240px" class="sidebar">
      <div class="logo">
        <el-icon size="28"><Reading /></el-icon>
        <span>书店管理</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#1e1e2d"
        text-color="#a2a3b7"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/books">
          <el-icon><Document /></el-icon>
          <span>书籍管理</span>
        </el-menu-item>
        <el-menu-item index="/categories">
          <el-icon><Menu /></el-icon>
          <span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><List /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="36" :icon="UserFilled" />
              <span>管理员</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { UserFilled } from '@element-plus/icons-vue'

const router = useRouter()

const handleCommand = (command) => {
  if (command === 'logout') {
    localStorage.removeItem('admin_token')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background: #1e1e2d;
  overflow-y: auto;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: white;
  font-size: 18px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.header {
  background: white;
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 24px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--gray-700);
  outline: none;
}

.user-info:focus,
.user-info:focus-visible {
  outline: none;
  box-shadow: none;
}

.main-content {
  background: #f5f7fa;
  padding: 24px;
  overflow-y: auto;
}

.el-menu-item {
  margin: 4px 8px;
  border-radius: 8px;
}

.el-menu-item.is-active {
  background: var(--primary) !important;
}
</style>
