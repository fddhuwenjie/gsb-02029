<template>
  <div class="publish-page">
    <div class="container">
      <div class="publish-card">
        <h1>发布二手书</h1>
        <p class="subtitle">让你的闲置书籍找到新主人</p>
        
        <form @submit.prevent="handleSubmit">
          <div class="form-row">
            <div class="form-group">
              <label>书名 *</label>
              <input type="text" v-model="form.title" class="input" required />
            </div>
            <div class="form-group">
              <label>作者</label>
              <input type="text" v-model="form.author" class="input" />
            </div>
          </div>
          
          <div class="form-row">
            <div class="form-group">
              <label>ISBN</label>
              <input type="text" v-model="form.isbn" class="input" />
            </div>
            <div class="form-group">
              <label>出版社</label>
              <input type="text" v-model="form.publisher" class="input" />
            </div>
          </div>
          
          <div class="form-row">
            <div class="form-group">
              <label>分类 *</label>
              <select v-model="form.categoryId" class="input" required>
                <option value="">请选择分类</option>
                <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                  {{ cat.name }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label>成色 *</label>
              <select v-model="form.quality" class="input" required>
                <option value="">请选择成色</option>
                <option value="全新">全新</option>
                <option value="九成新">九成新</option>
                <option value="八成新">八成新</option>
                <option value="七成新">七成新</option>
                <option value="六成新">六成新</option>
              </select>
            </div>
          </div>
          
          <div class="form-row">
            <div class="form-group">
              <label>原价</label>
              <input type="number" v-model="form.originalPrice" class="input" step="0.01" min="0" />
            </div>
            <div class="form-group">
              <label>售价 *</label>
              <input type="number" v-model="form.price" class="input" step="0.01" min="0" required />
            </div>
            <div class="form-group">
              <label>库存 *</label>
              <input type="number" v-model="form.stock" class="input" min="1" required />
            </div>
          </div>
          
          <div class="form-group">
            <label>封面图片</label>
            <div class="upload-area">
              <div class="preview" v-if="form.coverImage">
                <img :src="form.coverImage" alt="封面预览" />
                <button type="button" class="remove-btn" @click="removeCover">×</button>
              </div>
              <label class="upload-btn" v-else>
                <input type="file" accept="image/*" @change="handleUpload" hidden />
                <span v-if="uploading" class="upload-text">上传中...</span>
                <span v-else class="upload-text">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                    <polyline points="17 8 12 3 7 8"></polyline>
                    <line x1="12" y1="3" x2="12" y2="15"></line>
                  </svg>
                  点击上传封面
                </span>
              </label>
            </div>
            <p class="upload-tip">支持 JPG、PNG 格式，大小不超过 5MB</p>
          </div>
          
          <div class="form-group">
            <label>书籍简介</label>
            <textarea v-model="form.description" class="input" rows="4"></textarea>
          </div>
          
          <button type="submit" class="btn btn-primary btn-lg" :disabled="submitting">
            {{ submitting ? '发布中...' : '发布书籍' }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '../composables/useToast'
import api from '../api'

const router = useRouter()
const toast = useToast()
const categories = ref([])
const submitting = ref(false)
const uploading = ref(false)

const form = ref({
  title: '',
  author: '',
  isbn: '',
  publisher: '',
  categoryId: '',
  quality: '',
  originalPrice: '',
  price: '',
  stock: 1,
  coverImage: '',
  description: ''
})

const fetchCategories = async () => {
  try {
    const res = await api.get('/categories')
    if (res.code === 200) categories.value = res.data
  } catch (e) {
    console.error(e)
  }
}

const handleUpload = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  
  if (file.size > 5 * 1024 * 1024) {
    toast.warning('图片大小不能超过5MB')
    return
  }
  
  uploading.value = true
  const formData = new FormData()
  formData.append('file', file)
  
  try {
    const res = await api.post('/upload/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.code === 200) {
      form.value.coverImage = res.data
      toast.success('图片上传成功')
    } else {
      toast.error(res.message || '上传失败')
    }
  } catch (e) {
    toast.error('上传失败')
  }
  uploading.value = false
}

const removeCover = () => {
  form.value.coverImage = ''
}

const resetForm = () => {
  form.value = {
    title: '',
    author: '',
    isbn: '',
    publisher: '',
    categoryId: '',
    quality: '',
    originalPrice: '',
    price: '',
    stock: 1,
    coverImage: '',
    description: ''
  }
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    const submitData = {
      ...form.value,
      price: form.value.price ? Number(form.value.price) : null,
      originalPrice: form.value.originalPrice ? Number(form.value.originalPrice) : null,
      stock: form.value.stock ? Number(form.value.stock) : 1,
      categoryId: form.value.categoryId ? Number(form.value.categoryId) : null
    }
    const res = await api.post('/books', submitData)
    if (res.code === 200) {
      toast.success('发布成功！书籍已上架')
      resetForm()
      setTimeout(() => router.push('/books'), 1500)
    } else {
      toast.error(res.message || '发布失败')
    }
  } catch (e) {
    if (e.response?.status === 401) {
      toast.error('登录已失效，请重新登录')
    } else {
      toast.error(e.response?.data?.message || '发布失败，请检查表单信息')
    }
  }
  submitting.value = false
}

onMounted(() => {
  resetForm()
  fetchCategories()
})
</script>

<style scoped>
.publish-page {
  padding: 40px 0;
}

.publish-card {
  max-width: 720px;
  margin: 0 auto;
  background: white;
  border-radius: var(--radius-lg);
  padding: 48px;
  box-shadow: var(--shadow-lg);
}

.publish-card h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--gray-800);
  margin-bottom: 8px;
}

.subtitle {
  color: var(--gray-500);
  margin-bottom: 32px;
}

.form-row {
  display: flex;
  gap: 20px;
}

.form-row .form-group {
  flex: 1;
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

select.input {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%236b7280' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 36px;
}

textarea.input {
  resize: vertical;
}

.upload-area {
  border: 2px dashed var(--gray-200);
  border-radius: var(--radius);
  padding: 20px;
  text-align: center;
}

.upload-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  cursor: pointer;
  color: var(--gray-500);
  transition: all 0.2s;
}

.upload-btn:hover {
  color: var(--primary);
}

.upload-text {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.upload-text svg {
  flex-shrink: 0;
}

.upload-tip {
  font-size: 12px;
  color: var(--gray-400);
  margin-top: 8px;
}

.preview {
  position: relative;
  display: inline-block;
}

.preview img {
  max-width: 200px;
  max-height: 280px;
  border-radius: var(--radius-sm);
  object-fit: cover;
}

.preview .remove-btn {
  position: absolute;
  top: -10px;
  right: -10px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--danger);
  color: white;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
</style>
