<template>
  <div class="categories-page">
    <div class="page-header" style="display: flex; justify-content: space-between; align-items: center">
      <h2>分类管理</h2>
      <el-button type="primary" @click="showAdd">添加分类</el-button>
    </div>
    
    <el-card>
      <el-table :data="categories" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" />
        <el-table-column prop="icon" label="图标" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="editCategory(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="deleteCategory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑分类' : '添加分类'" width="400px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="请输入图标标识" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = ref({ name: '', icon: '', sortOrder: 0, status: 1 })

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  icon: [{ required: true, message: '请输入图标标识', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const fetchCategories = async () => {
  loading.value = true
  try {
    const res = await api.get('/admin/categories')
    if (res.code === 200) {
      categories.value = res.data
    }
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}

const showAdd = () => {
  isEdit.value = false
  form.value = { name: '', icon: '', sortOrder: 0, status: 1 }
  dialogVisible.value = true
}

const editCategory = (category) => {
  isEdit.value = true
  form.value = { ...category }
  dialogVisible.value = true
}

const saveCategory = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    saving.value = true
    if (isEdit.value) {
      await api.put(`/admin/categories/${form.value.id}`, form.value)
    } else {
      await api.post('/admin/categories', form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchCategories()
  } catch (e) {
    if (e !== false) {
      ElMessage.error('保存失败')
    }
  }
  saving.value = false
}

const deleteCategory = async (category) => {
  try {
    await ElMessageBox.confirm('确定删除该分类?', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await api.delete(`/admin/categories/${category.id}`)
    ElMessage.success('删除成功')
    fetchCategories()
  } catch (e) {
    if (e !== 'cancel') {
      const msg = e.response?.data?.message || '删除失败'
      ElMessage.error(msg)
    }
  }
}

onMounted(fetchCategories)
</script>
