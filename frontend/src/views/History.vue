<template>
  <div>
    <Card title="Alert History">
      <Tag color="green" v-if="sseConnected">SSE Connected</Tag>
      <Tag color="red" v-else>SSE Disconnected</Tag>
      <Table :columns="columns" :data="data" :loading="loading" class="mt-2"></Table>
      <Page :total="total" :page-size="pageSize" :current="page" @on-change="onPageChange" class="mt-2" />
    </Card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import request from '../api/request.js'

const loading = ref(false)
const data = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const sseConnected = ref(false)
let eventSource = null

// #AI-CODE-dev_release/v0.0.1-20260604: add SSE reconnection with exponential backoff
const reconnectAttempts = ref(0)
const maxReconnectAttempts = 10
const baseDelayMs = 1000

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: 'Rule ID', key: 'alertRuleId' },
  { title: 'Status', key: 'status' },
  { title: 'Sub Status', key: 'subStatus' },
  { title: 'Retry', key: 'retryCount' },
  { title: 'Created At', key: 'createdAt' }
]

const fetch = async () => {
  loading.value = true
  const res = await request.get('/alerts/history', { params: { current: page.value, size: pageSize.value } })
  data.value = res.data?.records || []
  total.value = res.data?.total || 0
  loading.value = false
}

const onPageChange = (p) => {
  page.value = p
  fetch()
}

const connectSse = () => {
  if (eventSource) {
    eventSource.close()
  }
  eventSource = new EventSource('/api/v1/sse/alerts')

  eventSource.onopen = () => {
    sseConnected.value = true
    reconnectAttempts.value = 0
  }

  eventSource.onerror = () => {
    sseConnected.value = false
    eventSource.close()
    handleReconnect()
  }

  eventSource.addEventListener('alerts', (e) => {
    const newAlerts = JSON.parse(e.data)
    data.value = [...newAlerts, ...data.value].slice(0, pageSize.value)
    total.value += newAlerts.length
  })
}

const handleReconnect = () => {
  if (reconnectAttempts.value >= maxReconnectAttempts) {
    console.error('Max SSE reconnection attempts reached')
    return
  }
  const delay = Math.min(baseDelayMs * Math.pow(2, reconnectAttempts.value), 30000)
  reconnectAttempts.value++
  console.log(`SSE reconnect attempt ${reconnectAttempts.value}/${maxReconnectAttempts} in ${delay}ms`)
  setTimeout(connectSse, delay)
}

onMounted(() => {
  fetch()
  reconnectAttempts.value = 0
  connectSse()
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})
</script>

<style scoped>
.mt-2 { margin-top: 12px; }
</style>
