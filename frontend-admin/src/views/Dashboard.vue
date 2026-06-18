<template>
  <div class="dashboard">
    <div class="page-header">
      <h2>数据概览</h2>
    </div>
    
    <el-row :gutter="24">
      <el-col :span="6">
        <div class="stat-card">
          <div class="icon blue"><el-icon size="24"><User /></el-icon></div>
          <div class="value">{{ stats.userCount || 0 }}</div>
          <div class="label">注册用户</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="icon green"><el-icon size="24"><Document /></el-icon></div>
          <div class="value">{{ stats.bookCount || 0 }}</div>
          <div class="label">在售书籍</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="icon purple"><el-icon size="24"><List /></el-icon></div>
          <div class="value">{{ stats.orderCount || 0 }}</div>
          <div class="label">总订单数</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="icon orange"><el-icon size="24"><Money /></el-icon></div>
          <div class="value">¥{{ stats.totalSales || 0 }}</div>
          <div class="label">销售总额</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="24" style="margin-top: 24px">
      <el-col :span="24">
        <el-card>
          <template #header>
            <span style="font-weight: 600">平台概况</span>
          </template>
          <div ref="chartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import api from '../api'

const stats = ref({})
const chartRef = ref(null)

const fetchDashboard = async () => {
  try {
    const res = await api.get('/admin/dashboard')
    if (res.code === 200) {
      stats.value = res.data
    }
  } catch (e) {
    console.error(e)
  }
}

const initChart = () => {
  const chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '订单量',
        type: 'line',
        smooth: true,
        data: [12, 19, 15, 25, 22, 30, 28],
        areaStyle: { color: 'rgba(99, 102, 241, 0.1)' },
        lineStyle: { color: '#6366f1' },
        itemStyle: { color: '#6366f1' }
      }
    ]
  })
}

onMounted(() => {
  fetchDashboard()
  initChart()
})
</script>
