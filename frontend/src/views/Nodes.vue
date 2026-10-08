<template>
  <div class="nodes">
    <Row :gutter="16">
      <Col span="6">
        <Card :bordered="false" class="summary-card">
          <div class="summary-icon gateway"><Icon type="ios-git-network" size="22" /></div>
          <div>
            <div class="summary-title">Gateway 节点</div>
            <div class="summary-value">
              <span class="available">{{ summary.gatewayAvailable }}</span>
              <span class="total">/ {{ summary.gatewayTotal }}</span>
              <span class="summary-note">可用</span>
            </div>
          </div>
        </Card>
      </Col>
      <Col span="6">
        <Card :bordered="false" class="summary-card">
          <div class="summary-icon worker"><Icon type="ios-hammer-outline" size="22" /></div>
          <div>
            <div class="summary-title">Worker 节点</div>
            <div class="summary-value">
              <span class="available">{{ summary.workerAvailable }}</span>
              <span class="total">/ {{ summary.workerTotal }}</span>
              <span class="summary-note">可用</span>
            </div>
          </div>
        </Card>
      </Col>
      <Col span="12">
        <Card :bordered="false" class="legend-card">
          <div class="legend-note">
            <Icon type="ios-information-circle-outline" size="16" />
            <span>节点每 10 秒上报一次心跳；超过 30 秒无心跳视为不可用。停用 Worker 后，其监听会自动暂停，消息由其他 Worker 继续处理。权重用于未来多节点负载调度。</span>
          </div>
        </Card>
      </Col>
    </Row>

    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">节点列表</span>
        <Button size="small" @click="fetchAll" :loading="loading">
          <Icon type="ios-refresh" /> 刷新
        </Button>
      </div>
      <Table :columns="columns" :data="nodes" :loading="loading"></Table>
    </Card>

    <Modal v-model="weightVisible" title="修改节点权重" @on-ok="submitWeight">
      <Form :model="weightForm" :label-width="90">
        <FormItem label="节点">
          <Input :model-value="weightForm.nodeId" disabled />
        </FormItem>
        <FormItem label="权重">
          <InputNumber v-model="weightForm.weight" :min="0" :max="100" />
          <span class="form-tip">0 = 不参与调度，数值越大处理优先级越高</span>
        </FormItem>
      </Form>
    </Modal>

    <Modal v-model="testVisible" title="节点可用性测试" :footer-hide="true" width="480">
      <div class="test-body">
        <Spin fix v-if="testing"></Spin>
        <div v-if="testResult" class="test-result">
        <Row :gutter="8" class="test-rows">
          <Col span="12">
            <div class="test-item">
              <Tag :color="testResult.reachable ? 'green' : 'red'">
                {{ testResult.reachable ? '可访问' : '不可访问' }}
              </Tag>
              <div class="test-label">HTTP 连通性</div>
            </div>
          </Col>
          <Col span="12">
            <div class="test-item">
              <Tag :color="testResult.detail?.healthy ? 'green' : 'red'">
                {{ testResult.detail?.healthy ? '健康' : '异常' }}
              </Tag>
              <div class="test-label">整体健康度</div>
            </div>
          </Col>
          <Col span="12">
            <div class="test-item">
              <Tag :color="testResult.detail?.db ? 'green' : 'red'">
                {{ testResult.detail?.db ? '正常' : '异常' }}
              </Tag>
              <div class="test-label">数据库连接</div>
            </div>
          </Col>
          <Col span="12">
            <div class="test-item">
              <Tag :color="testResult.detail?.mq ? 'green' : 'red'">
                {{ testResult.detail?.mq ? '正常' : '异常' }}
              </Tag>
              <div class="test-label">消息队列连接</div>
            </div>
          </Col>
        </Row>
        <div class="test-url">{{ testResult.url }}</div>
        <Alert v-if="!testResult.reachable" type="error" show-icon>
          {{ testResult.error }}
        </Alert>
        <pre v-if="testResult.detail" class="test-detail">{{ JSON.stringify(testResult.detail, null, 2) }}</pre>
        </div>
      </div>
    </Modal>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, h } from 'vue'
import { Tag, Button, Modal, Message } from 'view-ui-plus'
import request from '../api/request.js'

const nodes = ref([])
const summary = ref({ gatewayTotal: 0, gatewayAvailable: 0, workerTotal: 0, workerAvailable: 0 })
const loading = ref(false)

const weightVisible = ref(false)
const weightForm = ref({ id: null, nodeId: '', weight: 1 })

const testVisible = ref(false)
const testing = ref(false)
const testResult = ref(null)

const renderActions = (h, params) => {
  const row = params.row
  const isWorker = row.nodeType === 'WORKER'
  const disabled = row.status === 'DISABLED'
  const actions = []
  if (isWorker) {
    actions.push(h(Button, {
      props: { size: 'small', type: disabled ? 'success' : 'warning' },
      on: { click: () => toggleStatus(row) }
    }, () => disabled ? '启用' : '停用'))
  }
  actions.push(h(Button, {
    props: { size: 'small' },
    on: { click: () => openWeight(row) }
  }, () => '权重'))
  actions.push(h(Button, {
    props: { size: 'small' },
    on: { click: () => testNode(row) }
  }, () => '测试'))
  if (!row.available) {
    actions.push(h(Button, {
      props: { size: 'small', type: 'error' },
      on: { click: () => removeStale(row) }
    }, () => '删除'))
  }
  return h('div', { class: 'action-group' }, actions)
}

const columns = [
  { title: '节点 ID', key: 'nodeId', minWidth: 170 },
  {
    title: '类型', key: 'nodeType', width: 100,
    render: (h, params) => {
      const isWorker = params.row.nodeType === 'WORKER'
      return h(Tag, { props: { color: isWorker ? 'blue' : 'green' } },
        () => isWorker ? 'Worker' : 'Gateway')
    }
  },
  {
    title: '地址', key: 'host', minWidth: 150,
    render: (h, params) => h('span', { class: 'host-cell' }, `${params.row.host}:${params.row.port}`)
  },
  {
    title: '状态', key: 'status', width: 110,
    render: (h, params) => {
      const disabled = params.row.status === 'DISABLED'
      return h(Tag, { props: { color: disabled ? 'red' : 'green' } },
        () => disabled ? '已停用' : '运行中')
    }
  },
  {
    title: '可用性', key: 'available', width: 110,
    render: (h, params) => {
      const ok = params.row.available && params.row.status !== 'DISABLED'
      return h(Tag, { props: { color: ok ? 'green' : 'orange' } },
        () => ok ? '在线' : (params.row.available ? '已停用' : '离线'))
    }
  },
  { title: '权重', key: 'weight', width: 80, align: 'center' },
  {
    title: '最后心跳', key: 'lastHeartbeatAt', width: 180,
    render: (h, params) => h('span', { class: 'heartbeat-cell' }, fmtTime(params.row.lastHeartbeatAt))
  },
  { title: '操作', width: 180, render: renderActions }
]

const fmtTime = (t) => {
  if (!t) return '--'
  return String(t).replace('T', ' ').slice(0, 19)
}

const fetchAll = async () => {
  loading.value = true
  try {
    const [listRes, summaryRes] = await Promise.all([
      request.get('/nodes'),
      request.get('/nodes/summary')
    ])
    nodes.value = listRes.data || []
    summary.value = summaryRes.data
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const toggleStatus = async (row) => {
  const target = row.status === 'DISABLED' ? 'ENABLED' : 'DISABLED'
  try {
    await request.put(`/nodes/${row.id}/status`, { status: target })
    Message.success({ content: target === 'ENABLED' ? `节点 ${row.nodeId} 已启用` : `节点 ${row.nodeId} 已停用` })
    fetchAll()
  } catch (e) {
    console.error(e)
  }
}

const openWeight = (row) => {
  weightForm.value = { id: row.id, nodeId: row.nodeId, weight: row.weight }
  weightVisible.value = true
}

// only offline (stale) nodes show the delete button; backend rejects deleting online nodes anyway
const removeStale = (row) => {
  Modal.confirm({
    title: '删除离线节点',
    content: `确定从注册表中删除离线节点 ${row.nodeId} 吗？若它重新上线会自动重新注册。`,
    onOk: async () => {
      try {
        await request.delete(`/nodes/${row.id}`)
        Message.success({ content: `已删除离线节点 ${row.nodeId}` })
        fetchAll()
      } catch (e) {
        Message.error({ content: e.response?.data?.error || '删除失败' })
      }
    }
  })
}

const submitWeight = async () => {
  try {
    await request.put(`/nodes/${weightForm.value.id}/weight`, { weight: weightForm.value.weight })
    Message.success({ content: '权重已更新' })
    weightVisible.value = false
    fetchAll()
  } catch (e) {
    console.error(e)
  }
}

const testNode = async (row) => {
  testVisible.value = true
  testing.value = true
  testResult.value = null
  try {
    const res = await request.post(`/nodes/${row.id}/test`)
    testResult.value = res.data
  } catch (e) {
    console.error(e)
  } finally {
    testing.value = false
  }
}

let timer = null

onMounted(() => {
  fetchAll()
  timer = setInterval(fetchAll, 15000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.summary-card {
  display: flex;
  align-items: center;
  gap: 14px;
  border-radius: 12px;
}

.summary-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.summary-icon.gateway { background: rgba(127, 191, 154, 0.14); color: var(--aw-accent); }
.summary-icon.worker { background: rgba(127, 180, 201, 0.14); color: var(--aw-info); }

.summary-title { font-size: 13px; color: var(--aw-text-secondary); margin-bottom: 2px; }
.summary-value { font-size: 24px; font-weight: 600; color: var(--aw-text); }
.summary-value .available { color: var(--aw-success); }
.summary-value .total { color: var(--aw-text-faint); font-weight: 400; }
.summary-note { font-size: 12px; color: var(--aw-text-faint); font-weight: 400; margin-left: 8px; }

.legend-card { border-radius: 12px; display: flex; align-items: center; height: 100%; }
.legend-note {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  color: var(--aw-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.legend-note .ivu-icon { color: var(--aw-accent); margin-top: 3px; }

.table-card { margin-top: 16px; border-radius: 12px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.card-title { font-size: 15px; font-weight: 600; color: var(--aw-text); }

.action-group { display: flex; gap: 6px; }
.host-cell { font-family: Consolas, monospace; font-size: 12px; color: var(--aw-text-secondary); }
.heartbeat-cell { font-size: 12px; color: var(--aw-text-faint); font-variant-numeric: tabular-nums; }

.form-tip { margin-left: 10px; font-size: 12px; color: var(--aw-text-faint); }

.test-body { position: relative; min-height: 200px; }
.test-result { min-height: 200px; position: relative; }
.test-rows { margin-bottom: 10px; }
.test-item {
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.test-label { font-size: 12px; color: var(--aw-text-secondary); }
.test-url {
  font-family: Consolas, monospace;
  font-size: 12px;
  color: var(--aw-text-faint);
  background: var(--aw-panel);
  padding: 6px 10px;
  border-radius: 6px;
  margin: 10px 0;
  word-break: break-all;
}
.test-detail {
  background: var(--aw-bg-deep);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 12px;
  font-size: 12px;
  color: var(--aw-text-secondary);
  max-height: 220px;
  overflow: auto;
}
</style>
