<template>
  <div class="users-page">
    <div class="page-header">
      <h2>用户管理</h2>
    </div>
    
    <el-card>
      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="role" label="角色">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button 
              v-if="row.role !== 'ADMIN'"
              :type="row.status === 1 ? 'warning' : 'success'" 
              size="small"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="fetchUsers"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const users = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await api.get('/admin/users', { params: { page: page.value - 1, size: 10 } })
    if (res.code === 200) {
      users.value = res.data.content
      total.value = res.data.totalElements
    }
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}

const toggleStatus = async (user) => {
  try {
    await api.put(`/admin/users/${user.id}/status`, { status: user.status === 1 ? 0 : 1 })
    ElMessage.success('操作成功')
    fetchUsers()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

onMounted(fetchUsers)
</script>
