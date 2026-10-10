// #AI-CODE-FILE-dev_release/v0.0.1-20260901: unify theme + Chinese UI + dead-letter retry button
<template>
  <div>
    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">失败消息</span>
      </div>
      <Tabs v-model="activeTab">
        <TabPane label="失败消息" name="failed">
          <Table :columns="failedColumns" :data="failedData" :loading="loading">
            <template #action="{ row }">
              <Button size="small" @click="openDetail(row.id)">详情</Button>
            </template>
          </Table>
          <div class="pager">
            <Page :total="failedTotal" :page-size="pageSize" :current="failedPage" @on-change="p => changePage('failed', p)" />
          </div>
        </TabPane>
        <TabPane label="死信队列" name="dead">
          <Table :columns="deadColumns" :data="deadData" :loading="loading">
            <template #action="{ row }">
              <Button size="small" type="primary" :loading="retryingId === row.id" @click="retry(row)">重试</Button>
            </template>
          </Table>
          <div class="pager">
            <Page :total="deadTotal" :page-size="pageSize" :current="deadPage" @on-change="p => changePage('dead', p)" />
          </div>
        </TabPane>
      </Tabs>
    </Card>

    <Modal v-model="detailVisible" title="失败消息详情" width="720" :footer-hide="true">
      <Spin fix v-if="detailLoading"></Spin>
      <div v-if="detail">
        <div class="detail-grid">
          <div class="detail-item"><label>告警 ID</label><span class="mono">{{ detail.messageNo || detail.id }}</span></div>
          <div class="detail-item"><label>规则 ID</label><span>{{ detail.alertRuleId || '--' }}</span></div>
          <div class="detail-item"><label>状态</label><span>{{ detail.status }}</span></div>
          <div class="detail-item"><label>子状态</label><span>{{ detail.subStatus || '--' }}</span></div>
          <div class="detail-item"><label>重试次数</label><span>{{ detail.retryCount }}</span></div>
        </div>
        <div class="raw-title">原始报文</div>
        <pre class="raw-payload">{{ detail.rawPayload || '（无原始报文记录）' }}</pre>
      </div>
    </Modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Message } from 'view-ui-plus'
import request, { gatewayRequest } from '../api/request.js'
import { fmtTime } from '../utils/time.js'

const loading = ref(false)
const activeTab = ref('failed')
const failedData = ref([])
const deadData = ref([])
const failedTotal = ref(0)
const deadTotal = ref(0)
const failedPage = ref(1)
const deadPage = ref(1)
const pageSize = ref(20)

const retryingId = ref(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const failedColumns = [
  { title: '告警 ID', key: 'messageNo', minWidth: 180 },
  { title: '规则 ID', key: 'alertRuleId', width: 90 },
  { title: '子状态', key: 'subStatus', width: 120 },
  { title: '重试', key: 'retryCount', width: 70, align: 'center' },
  { title: '时间', key: 'createdAt', minWidth: 160,
    render: (h, p) => h('span', {}, fmtTime(p.row.createdAt)) },
  { title: '操作', slot: 'action', width: 90 }
]

const deadColumns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '关联告警 ID', key: 'alertMessageId', width: 120 },
  { title: '错误信息', key: 'errorInfo', minWidth: 260 },
  { title: '时间', key: 'createdAt', width: 170,
    render: (h, p) => h('span', {}, fmtTime(p.row.createdAt)) },
  { title: '操作', slot: 'action', width: 90 }
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

const openDetail = async (id) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await request.get(`/alerts/history/${id}`)
    detail.value = res.data
  } catch (e) {
    console.error(e)
  } finally {
    detailLoading.value = false
  }
}

const retry = async (row) => {
  retryingId.value = row.id
  try {
    const res = await gatewayRequest.post(`/alerts/dead-letters/${row.id}/retry`)
    Message.success({ content: `已重新入队，新告警 ID：${res.data?.messageId || '--'}` })
    fetchDead()
  } catch (e) {
    Message.error({ content: e.response?.data?.error || '重试失败' })
  } finally {
    retryingId.value = null
  }
}

const refreshAll = async () => {
  loading.value = true
  try {
    await Promise.all([fetchFailed(), fetchDead()])
  } finally {
    loading.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.table-card { border-radius: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--aw-text); }
.pager { margin-top: 12px; text-align: right; }

.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.detail-item {
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 10px 12px;
}
.detail-item label {
  display: block;
  font-size: 12px;
  color: var(--aw-text-faint);
  margin-bottom: 4px;
}
.detail-item .mono { font-family: Consolas, monospace; color: var(--aw-accent); }
.raw-title { font-size: 13px; font-weight: 600; color: var(--aw-text); margin: 18px 0 10px; }
.raw-payload {
  background: var(--aw-bg-deep);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 14px;
  font-size: 12px;
  color: var(--aw-text-secondary);
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
