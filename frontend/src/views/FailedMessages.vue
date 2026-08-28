<template>
  <div>
    <Card title="Failed Messages">
      <Tabs>
        <TabPane label="Failed" name="failed">
          <Table :columns="failedColumns" :data="failedData" :loading="loading"></Table>
          <Page :total="failedTotal" :page-size="pageSize" :current="failedPage" @on-change="p => changePage('failed', p)" class="mt-2" />
        </TabPane>
        <TabPane label="Dead Letters" name="dead">
          <Table :columns="deadColumns" :data="deadData" :loading="loading"></Table>
          <Page :total="deadTotal" :page-size="pageSize" :current="deadPage" @on-change="p => changePage('dead', p)" class="mt-2" />
        </TabPane>
      </Tabs>
    </Card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request.js'

const loading = ref(false)
const failedData = ref([])
const deadData = ref([])
const failedTotal = ref(0)
const deadTotal = ref(0)
const failedPage = ref(1)
const deadPage = ref(1)
const pageSize = ref(20)

const failedColumns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: 'Rule ID', key: 'alertRuleId' },
  { title: 'Sub Status', key: 'subStatus' },
  { title: 'Retry', key: 'retryCount' },
  { title: 'Created At', key: 'createdAt' }
]

const deadColumns = [
  { title: 'ID', key: 'id' },
  { title: 'Message ID', key: 'alertMessageId' },
  { title: 'Error Info', key: 'errorInfo' },
  { title: 'Created At', key: 'createdAt' }
]

const fetchFailed = async () => {
  const res = await request.get('/alerts/failed', { params: { current: failedPage.value, size: pageSize.value } })
  failedData.value = res.data?.records || []
  failedTotal.value = res.data?.total || 0
}

const fetchDead = async () => {
  const res = await request.get('/alerts/dead-letters', { params: { current: deadPage.value, size: pageSize.value } })
  deadData.value = res.data?.records || []
  deadTotal.value = res.data?.total || 0
}

const changePage = (type, p) => {
  if (type === 'failed') {
    failedPage.value = p
    fetchFailed()
  } else {
    deadPage.value = p
    fetchDead()
  }
}

onMounted(async () => {
  loading.value = true
  await Promise.all([fetchFailed(), fetchDead()])
  loading.value = false
})
</script>

<style scoped>
.mt-2 { margin-top: 12px; }
</style>