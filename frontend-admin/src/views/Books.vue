<template>
  <div class="books-page">
    <div class="page-header">
      <h2>书籍管理</h2>
      <el-button type="primary" @click="showAdd">
        <el-icon><Plus /></el-icon>
        添加书籍
      </el-button>
    </div>
    
    <!-- 搜索筛选 -->
    <el-card style="margin-bottom: 16px">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="书名">
          <el-input v-model="searchForm.title" placeholder="请输入书名" clearable />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="searchForm.author" placeholder="请输入作者" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.categoryId" placeholder="请选择分类" clearable style="width: 150px">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchBooks">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <el-card>
      <el-table :data="books" v-loading="loading" stripe>
        <el-table-column prop="id" label="编号" width="70" />
        <el-table-column label="封面" width="70">
          <template #default="{ row }">
            <el-image :src="row.coverImage || '/images/default-cover.svg'" style="width: 40px; height: 56px" fit="cover">
              <template #error>
                <img src="/images/default-cover.svg" style="width: 40px; height: 56px; object-fit: cover" />
              </template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="书名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="author" label="作者" min-width="110" show-overflow-tooltip />
        <el-table-column prop="isbn" label="ISBN" width="140" show-overflow-tooltip />
        <el-table-column label="分类" width="90">
          <template #default="{ row }">
            {{ getCategoryName(row.categoryId) }}
          </template>
        </el-table-column>
        <el-table-column prop="price" label="价格" width="80">
          <template #default="{ row }">¥{{ row.price?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="quality" label="成色" width="70" />
        <el-table-column prop="stock" label="库存" width="60" />
        <el-table-column prop="status" label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewBook(row)">查看</el-button>
            <el-button type="warning" size="small" @click="editBook(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="deleteBook(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="total"
        layout="total, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="fetchBooks"
      />
    </el-card>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewDialogVisible" title="书籍详情" width="650px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="书名">{{ viewForm.title }}</el-descriptions-item>
        <el-descriptions-item label="作者">{{ viewForm.author }}</el-descriptions-item>
        <el-descriptions-item label="ISBN">{{ viewForm.isbn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出版社">{{ viewForm.publisher || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ getCategoryName(viewForm.categoryId) }}</el-descriptions-item>
        <el-descriptions-item label="成色">{{ viewForm.quality || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原价">{{ viewForm.originalPrice ? '¥' + viewForm.originalPrice.toFixed(2) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="售价">¥{{ viewForm.price?.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="库存">{{ viewForm.stock }}</el-descriptions-item>
        <el-descriptions-item label="浏览量">{{ viewForm.viewCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="viewForm.status === 1 ? 'success' : 'info'">
            {{ viewForm.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewForm.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="简介" :span="2">{{ viewForm.description || '暂无简介' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="viewForm.coverImage" style="margin-top: 16px; text-align: center">
        <el-image :src="viewForm.coverImage" style="max-width: 200px" fit="contain" />
      </div>
    </el-dialog>

    <!-- 添加/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑书籍' : '添加书籍'" width="650px">
      <el-form :model="editForm" :rules="rules" ref="formRef" label-width="80px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="书名" prop="title">
              <el-input v-model="editForm.title" placeholder="请输入书名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作者" prop="author">
              <el-input v-model="editForm.author" placeholder="请输入作者" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="ISBN">
              <el-input v-model="editForm.isbn" placeholder="请输入ISBN" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出版社">
              <el-input v-model="editForm.publisher" placeholder="请输入出版社" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="editForm.categoryId" placeholder="请选择分类" style="width: 100%">
                <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成色">
              <el-select v-model="editForm.quality" placeholder="请选择成色" style="width: 100%">
                <el-option label="全新" value="全新" />
                <el-option label="九成新" value="九成新" />
                <el-option label="八成新" value="八成新" />
                <el-option label="七成新" value="七成新" />
                <el-option label="六成新" value="六成新" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="原价">
              <el-input-number v-model="editForm.originalPrice" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="售价" prop="price">
              <el-input-number v-model="editForm.price" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="editForm.stock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="封面">
          <div class="cover-wrapper">
            <div class="cover-uploader" @click="triggerUpload">
              <img v-if="editForm.coverImage" :src="editForm.coverImage" class="cover-preview" />
              <el-icon v-else class="cover-uploader-icon"><Plus /></el-icon>
            </div>
            <el-button v-if="editForm.coverImage" type="danger" size="small" circle class="cover-remove-btn" @click.stop="editForm.coverImage = ''">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <input ref="fileInputRef" type="file" accept="image/*" style="display: none" @change="handleFileChange" />
          <el-input v-model="editForm.coverImage" placeholder="或直接输入图片URL" style="margin-top: 8px" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入书籍简介" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveBook" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Close } from '@element-plus/icons-vue'
import api from '../api'

const books = ref([])
const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const total = ref(0)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const searchForm = ref({
  title: '',
  author: '',
  categoryId: '',
  status: ''
})

const editForm = ref({
  title: '',
  author: '',
  isbn: '',
  publisher: '',
  categoryId: '',
  quality: '',
  originalPrice: 0,
  price: 0,
  stock: 1,
  coverImage: '',
  description: '',
  status: 1
})

const viewForm = ref({})

const rules = {
  title: [{ required: true, message: '请输入书名', trigger: 'blur' }],
  author: [{ required: true, message: '请输入作者', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }]
}

const fileInputRef = ref(null)
const uploading = ref(false)

const triggerUpload = () => {
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
    fileInputRef.value.click()
  }
}

const handleFileChange = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await api.post('/upload/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.code === 200) {
      editForm.value.coverImage = res.data
      ElMessage.success('上传成功')
    } else {
      ElMessage.error('上传失败')
    }
  } catch (err) {
    ElMessage.error('上传失败')
  }
  uploading.value = false
}

const getCategoryName = (id) => {
  const cat = categories.value.find(c => c.id === id)
  return cat?.name || '-'
}

const fetchCategories = async () => {
  try {
    const res = await api.get('/admin/categories')
    if (res.code === 200) {
      categories.value = res.data
    }
  } catch (e) {
    console.error(e)
  }
}

const fetchBooks = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: 10 }
    if (searchForm.value.title) params.title = searchForm.value.title
    if (searchForm.value.author) params.author = searchForm.value.author
    if (searchForm.value.categoryId) params.categoryId = searchForm.value.categoryId
    if (searchForm.value.status !== '') params.status = searchForm.value.status
    
    const res = await api.get('/admin/books', { params })
    if (res.code === 200) {
      books.value = res.data.content
      total.value = res.data.totalElements
    }
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}

const resetSearch = () => {
  searchForm.value = { title: '', author: '', categoryId: '', status: '' }
  page.value = 1
  fetchBooks()
}

const showAdd = () => {
  isEdit.value = false
  editForm.value = {
    title: '',
    author: '',
    isbn: '',
    publisher: '',
    categoryId: '',
    quality: '',
    originalPrice: 0,
    price: 0,
    stock: 1,
    coverImage: '',
    description: '',
    status: 1
  }
  dialogVisible.value = true
}

const viewBook = (book) => {
  viewForm.value = { ...book }
  viewDialogVisible.value = true
}

const editBook = (book) => {
  isEdit.value = true
  editForm.value = { ...book }
  dialogVisible.value = true
}

const saveBook = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    saving.value = true
    
    if (isEdit.value) {
      await api.put(`/admin/books/${editForm.value.id}`, editForm.value)
      ElMessage.success('修改成功')
    } else {
      await api.post('/admin/books', editForm.value)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    fetchBooks()
  } catch (e) {
    if (e !== 'cancel' && e !== false) {
      ElMessage.error('保存失败')
    }
  }
  saving.value = false
}

const deleteBook = async (book) => {
  try {
    await ElMessageBox.confirm(`确定删除书籍"${book.title}"吗？`, '删除确认', { 
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await api.delete(`/admin/books/${book.id}`)
    ElMessage.success('删除成功')
    fetchBooks()
  } catch (e) {
    if (e !== 'cancel') {
      const msg = e.response?.data?.message || '删除失败'
      ElMessage.error(msg)
    }
  }
}

onMounted(() => {
  fetchCategories()
  fetchBooks()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.image-placeholder {
  width: 50px;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  color: #999;
  font-size: 12px;
}

.cover-wrapper {
  position: relative;
  display: inline-block;
}

.cover-uploader {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  width: 120px;
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cover-uploader:hover {
  border-color: #409eff;
}

.cover-preview {
  width: 120px;
  height: 160px;
  object-fit: cover;
  display: block;
}

.cover-uploader-icon {
  font-size: 28px;
  color: #8c939d;
}

.cover-remove-btn {
  position: absolute;
  top: -8px;
  right: -8px;
  z-index: 1;
}
</style>
