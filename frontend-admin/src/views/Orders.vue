<template>
  <div class="orders-page">
    <div class="page-header">
      <h2>订单管理</h2>
    </div>
    
    <el-card>
      <div style="margin-bottom: 16px">
        <el-select v-model="statusFilter" placeholder="订单状态" clearable @change="fetchOrders" style="width: 150px">
          <el-option label="待付款" value="PENDING" />
          <el-option label="已付款" value="PAID" />
          <el-option label="已发货" value="SHIPPED" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </div>
      
      <el-table :data="orders" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column prop="totalAmount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.totalAmount }}</template>
        </el-table-column>
        <el-table-column prop="receiver" label="收货人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="address" label="地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="trackingNo" label="快递单号" width="160">
          <template #default="{ row }">
            {{ row.trackingNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-select 
              v-if="row.status !== 'CANCELLED' && row.status !== 'COMPLETED'"
              :model-value="row.status" 
              size="small" 
              @change="(val) => handleStatusChange(row, val)"
              style="width: 100px"
            >
              <el-option label="待付款" value="PENDING" />
              <el-option label="已付款" value="PAID" />
              <el-option label="已发货" value="SHIPPED" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="fetchOrders"
      />
    </el-card>

    <el-dialog v-model="trackingDialogVisible" title="填写快递单号" width="400px">
      <el-form label-width="80px">
        <el-form-item label="快递单号">
          <el-input v-model="trackingNo" placeholder="请输入快递单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelShipping">取消</el-button>
        <el-button type="primary" @click="confirmShipping">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const orders = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const statusFilter = ref('')

const trackingDialogVisible = ref(false)
const trackingNo = ref('')
const pendingShipOrder = ref(null)
const previousStatus = ref('')

const statusMap = {
  PENDING: { text: '待付款', type: 'warning' },
  PAID: { text: '已付款', type: 'primary' },
  SHIPPED: { text: '已发货', type: '' },
  COMPLETED: { text: '已完成', type: 'success' },
  CANCELLED: { text: '已取消', type: 'info' }
}

const getStatusText = (status) => statusMap[status]?.text || status
const getStatusType = (status) => statusMap[status]?.type || ''

const fetchOrders = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: 10 }
    if (statusFilter.value) params.status = statusFilter.value
    const res = await api.get('/admin/orders', { params })
    if (res.code === 200) {
      orders.value = res.data.content
      total.value = res.data.totalElements
    }
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}

const handleStatusChange = (order, newStatus) => {
  if (newStatus === 'SHIPPED') {
    previousStatus.value = order.status
    pendingShipOrder.value = order
    trackingNo.value = order.trackingNo || ''
    trackingDialogVisible.value = true
  } else {
    updateStatus(order, newStatus)
  }
}

const cancelShipping = () => {
  if (pendingShipOrder.value) {
    pendingShipOrder.value.status = previousStatus.value
  }
  trackingDialogVisible.value = false
  pendingShipOrder.value = null
  trackingNo.value = ''
}

const confirmShipping = async () => {
  if (!trackingNo.value.trim()) {
    ElMessage.warning('请输入快递单号')
    return
  }
  if (pendingShipOrder.value) {
    try {
      await api.put(`/admin/orders/${pendingShipOrder.value.id}/status`, {
        status: 'SHIPPED',
        trackingNo: trackingNo.value.trim()
      })
      ElMessage.success('发货成功')
      trackingDialogVisible.value = false
      pendingShipOrder.value = null
      trackingNo.value = ''
      fetchOrders()
    } catch (e) {
      ElMessage.error('更新失败')
      fetchOrders()
    }
  }
}

const updateStatus = async (order, newStatus) => {
  try {
    await api.put(`/admin/orders/${order.id}/status`, { status: newStatus })
    ElMessage.success('状态更新成功')
    fetchOrders()
  } catch (e) {
    ElMessage.error('更新失败')
    fetchOrders()
  }
}

onMounted(fetchOrders)
</script>
