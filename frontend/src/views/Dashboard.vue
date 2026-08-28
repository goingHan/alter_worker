<template>
  <div class="dashboard">
    <Row :gutter="16">
      <Col span="6">
        <Card class="stat-card">
          <div class="stat-title">Total Alerts</div>
          <div class="stat-value">{{ stats.totalAlerts }}</div>
        </Card>
      </Col>
      <Col span="6">
        <Card class="stat-card">
          <div class="stat-title">Success</div>
          <div class="stat-value success">{{ stats.success }}</div>
        </Card>
      </Col>
      <Col span="6">
        <Card class="stat-card">
          <div class="stat-title">Failed</div>
          <div class="stat-value error">{{ stats.failed }}</div>
        </Card>
      </Col>
      <Col span="6">
        <Card class="stat-card">
          <div class="stat-title">Dead Letters</div>
          <div class="stat-value warning">{{ stats.deadLetters }}</div>
        </Card>
      </Col>
    </Row>

    <Card class="recent-card" title="Recent Alerts">
      <Table :columns="columns" :data="recentAlerts" size="small"></Table>
    </Card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request.js'

const stats = ref({ totalAlerts: 0, success: 0, failed: 0, deadLetters: 0 })
const recentAlerts = ref([])

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: 'Status', key: 'status' },
  { title: 'Sub Status', key: 'subStatus' },
  { title: 'Created At', key: 'createdAt' }
]

const fetchStats = async () => {
  try {
    const [historyRes, failedRes, dlqRes] = await Promise.all([
      request.get('/alerts/history?pageSize=1'),
      request.get('/alerts/failed?pageSize=1'),
      request.get('/alerts/dead-letters?pageSize=1')
    ])
    stats.value.totalAlerts = historyRes.data?.total || 0
    stats.value.failed = failedRes.data?.total || 0
    stats.value.deadLetters = dlqRes.data?.total || 0

    const successRes = await request.get('/alerts/delivery-logs?status=success&pageSize=1')
    stats.value.success = successRes.data?.total || 0

    const recentRes = await request.get('/alerts/history?pageSize=10')
    recentAlerts.value = (recentRes.data?.records || []).map(item => ({
      id: item.id,
      status: item.status,
      subStatus: item.subStatus,
      createdAt: item.createdAt
    }))
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.stat-card {
  text-align: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffffff 0%, #f1f5f9 100%);
  border: none;
}

.stat-title {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #334155;
}

.stat-value.success { color: #10b981; }
.stat-value.error { color: #ef4444; }
.stat-value.warning { color: #f59e0b; }

.recent-card {
  margin-top: 16px;
  border-radius: 12px;
}
</style>