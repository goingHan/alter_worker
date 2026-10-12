// #AI-CODE-FILE-dev_release/v0.0.1-20260901: add filters, rule/channel/dead-letter columns, retry, fix detail timing
<template>
  <div>
    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">告警历史</span>
        <Button size="small" @click="fetch" :loading="loading">
          <Icon type="ios-refresh" /> 刷新
        </Button>
      </div>

      <div class="filter-bar">
        <Input v-model="filters.messageNo" placeholder="消息 ID" clearable class="filter-item" @on-enter="search" />
        <Select v-model="filters.status" clearable filterable placeholder="状态" class="filter-item">
          <Option v-for="s in statusOptions" :key="s.value" :value="s.value">{{ s.label }}</Option>
        </Select>
        <!-- #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: unified history type filter (ALERT / TEST / REPORT) -->
        <Select v-model="filters.messageType" clearable filterable placeholder="类型" class="filter-item">
          <Option value="">全部</Option>
          <Option value="ALERT">告警</Option>
          <Option value="TEST">测试</Option>
          <Option value="REPORT">日报</Option>
        </Select>
        <DatePicker :value="filters.startDate" type="datetime" format="yyyy-MM-dd HH:mm:ss" placeholder="开始时间" clearable class="filter-item date-item" @on-change="setStartDate" />
        <DatePicker :value="filters.endDate" type="datetime" format="yyyy-MM-dd HH:mm:ss" placeholder="结束时间" clearable class="filter-item date-item" @on-change="setEndDate" />
        <Select v-model="filters.inDeadLetter" clearable filterable placeholder="死信队列" class="filter-item">
          <Option value="yes">在死信队列</Option>
          <Option value="no">不在死信队列</Option>
        </Select>
        <Input v-model="filters.ruleName" placeholder="规则名" clearable class="filter-item" @on-enter="search" />
        <Input v-model="filters.channelName" placeholder="渠道名" clearable class="filter-item" @on-enter="search" />
        <Button type="primary" size="small" @click="search">查询</Button>
        <Button size="small" @click="reset">重置</Button>
      </div>

      <Table :columns="columns" :data="data" :loading="loading">
        <template #content="{ row }">
          <Tooltip :content="formatContent(row)" placement="top" :max-width="560" transfer>
            <span class="summary-cell">{{ row.payload }}</span>
          </Tooltip>
        </template>
        <template #action="{ row }">
          <Button size="small" @click="openDetail(row.id)">详情</Button>
          <!-- #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: TEST/REPORT rows never flow through the alert pipeline,
               re-enqueueing them would be meaningless — hide the dead-letter retry for those types -->
          <Button v-if="row.inDeadLetter && row.messageType !== 'TEST' && row.messageType !== 'REPORT'" size="small" type="primary" @click="retry(row)">重试</Button>
        </template>
      </Table>
      <div class="pager">
        <Page :total="total" :page-size="pageSize" :current="page" show-sizer :page-size-opts="[10, 15, 20, 50, 100]" @on-change="onPageChange" @on-page-size-change="onPageSizeChange" />
      </div>
    </Card>

    <Modal v-model="detailVisible" title="告警详情" width="960" :footer-hide="true">
      <Spin fix v-if="detailLoading"></Spin>
      <div v-if="detail" class="detail">
        <div class="detail-grid">
          <div class="detail-item"><label>告警 ID</label><span class="mono">{{ detail.messageNo || detail.id }}</span></div>
          <div class="detail-item"><label>规则 ID</label><span>{{ detail.alertRuleId || '--' }}</span></div>
          <div class="detail-item"><label>状态</label>
            <Tag :color="detail.status === 'success' ? 'green' : detail.status === 'FAILED' ? 'red' : 'blue'">
              {{ detail.status }}
            </Tag>
          </div>
          <div class="detail-item"><label>重试次数</label><span>{{ detail.retryCount }}</span></div>
          <!-- #AI-CODE-FUNCTION-dev_release/v0.0.2-20261009: hosted-HTML report archive link -->
          <div v-if="detail.contentUrl" class="detail-item"><label>归档</label>
            <a :href="detail.contentUrl" target="_blank" rel="noopener" class="archive-link">查看完整日报</a>
          </div>
        </div>

        <div class="trace-title">耗时链路（毫秒）</div>
        <!-- #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: rows that never entered the MQ (silenced, unmatched,
             report-pending, test/report generated) have no gwSentAt — show a flat note instead of a fake timeline -->
        <div v-if="detail.gwSentAt" class="trace">
          <div class="trace-node">
            <div class="trace-dot"></div>
            <div class="trace-label">接收</div>
            <div class="trace-time">{{ fmtTime(detail.receivedAt) }}</div>
          </div>
          <div class="trace-seg" :class="{ done: detail.gwSentAt }">
            <span class="trace-seg-text">{{ gwMs }}</span>
          </div>
          <div class="trace-node">
            <div class="trace-dot" :class="{ on: detail.gwSentAt }"></div>
            <div class="trace-label">入队</div>
            <div class="trace-time">{{ fmtTime(detail.gwSentAt) }}</div>
          </div>
          <div class="trace-seg" :class="{ done: detail.workerReceivedAt }">
            <span class="trace-seg-text">{{ mqMs }}</span>
          </div>
          <div class="trace-node">
            <div class="trace-dot" :class="{ on: detail.workerReceivedAt }"></div>
            <div class="trace-label">Worker 接收</div>
            <div class="trace-time">{{ fmtTime(detail.workerReceivedAt) }}</div>
          </div>
          <div class="trace-seg" :class="{ done: detail.workerSentAt }">
            <span class="trace-seg-text">{{ workerMs }}</span>
          </div>
          <div class="trace-node">
            <div class="trace-dot" :class="{ on: detail.workerSentAt }"></div>
            <div class="trace-label">外部发送</div>
            <div class="trace-time">{{ fmtTime(detail.workerSentAt) }}</div>
          </div>
          <div class="trace-seg" :class="{ done: detail.finishedAt }">
            <span class="trace-seg-text">{{ finishMs }}</span>
          </div>
          <div class="trace-node">
            <div class="trace-dot" :class="{ on: detail.finishedAt }"></div>
            <div class="trace-label">完成</div>
            <div class="trace-time">{{ fmtTime(detail.finishedAt) }}</div>
          </div>
        </div>
        <div v-else class="trace-flat">
          <div v-for="node in ['接收', '入队', 'Worker 接收', '外部发送', '完成']" :key="node" class="trace-flat-node">
            <span class="trace-flat-label">{{ node }}</span>
            <span class="trace-flat-dash">—</span>
          </div>
          <div class="form-tip">该消息未进入消息队列（网关直接落库），无 MQ 耗时链路</div>
        </div>

        <div class="raw-title-row">
          <div class="raw-title">告警内容</div>
          <Button size="small" @click="copyText(fmtPayload, '告警内容')">复制</Button>
        </div>
        <pre class="raw-payload">{{ fmtPayload }}</pre>

        <div class="raw-title-row">
          <div class="raw-title">原始报文</div>
          <Button size="small" @click="copyText(fmtRawPayload, '原始报文')">复制</Button>
        </div>
        <pre class="raw-payload">{{ fmtRawPayload }}</pre>
      </div>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, h } from 'vue'
import { Tag, Button, Message } from 'view-ui-plus'
import request, { gatewayRequest } from '../api/request.js'
import { prettyJson } from '../utils/json'
import { fmtTime } from '../utils/time.js'

const loading = ref(false)
const data = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const filters = reactive({
  messageNo: '',
  status: '',
  messageType: '',
  startDate: '',
  endDate: '',
  inDeadLetter: '',
  ruleName: '',
  channelName: ''
})

const statusOptions = [
  { value: 'recieved', label: '已接收' },
  { value: 'GW_SENT', label: '已入队' },
  { value: 'DELIVERED', label: '处理中' },
  { value: 'WORKER_SENT', label: '已发送' },
  { value: 'FAILED', label: '失败' },
  { value: 'success', label: '成功' },
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: dropped at the gateway — silenced rule, or no matching rule
  { value: 'SILENCED', label: '已静默' },
  { value: 'UNMATCHED', label: '未匹配' },
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: stored for the daily report instead of being sent
  { value: 'REPORT_PENDING', label: '已入库待日报' },
  // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: parked for the warning digest instead of being sent one-by-one
  { value: 'WARNING_PENDING', label: '已入库待汇总' }
]

const columns = [
  { title: '告警 ID', key: 'messageNo', width: 180 },
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: unified type column (ALERT / TEST / REPORT)
  {
    title: '类型', key: 'messageType', width: 90, align: 'center',
    render: (h, p) => {
      const t = p.row.messageType
      const map = {
        ALERT: ['blue', '告警'],
        TEST: ['orange', '测试'],
        REPORT: ['purple', '日报'],
        WARNING_DIGEST: ['purple', '汇总']
      }
      const [color, text] = map[t] || ['default', t || '—']
      return h(Tag, { props: { color } }, () => text)
    }
  },
  { title: '内容', slot: 'content', minWidth: 600 },
  { title: '规则名', key: 'ruleName', width: 140, render: (h, p) => h('span', {}, p.row.ruleName || '--') },
  { title: '渠道', key: 'channelNames', width: 160, render: (h, p) => h('span', { class: 'channel-cell' }, p.row.channelNames || '--') },
  {
    title: '状态', key: 'status', width: 110,
    render: (h, params) => {
      const s = params.row.status
      const map = {
        success: ['st-success', '成功'],
        FAILED: ['st-failed', '失败'],
        DELIVERED: ['st-info', '处理中'],
        WORKER_SENT: ['st-info', '已发送'],
        GW_SENT: ['st-info', '已入队'],
        recieved: ['st-info', '已接收'],
        SILENCED: ['st-silenced', '已静默'],
        UNMATCHED: ['st-unmatched', '未匹配'],
        // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: stored for the daily report instead of being sent
        REPORT_PENDING: ['st-report-pending', '已入库待日报'],
        WARNING_PENDING: ['st-report-pending', '已入库待汇总']
      }
      const [cls, text] = map[s] || ['st-info', s || '--']
      return h('span', { class: 'status-badge ' + cls }, text)
    }
  },
  { title: '子状态', key: 'subStatus', width: 110 },
  {
    title: '死信', key: 'inDeadLetter', width: 80, align: 'center',
    render: (h, params) => params.row.inDeadLetter
      ? h(Tag, { props: { color: 'orange' } }, () => '是')
      : h('span', { class: 'dim' }, '—')
  },
  { title: '重试', key: 'retryCount', width: 70, align: 'center' },
  { title: '接收时间', key: 'createdAt', width: 165,
    render: (h, p) => h('span', {}, fmtTime(p.row.createdAt)) },
  { title: '操作', slot: 'action', width: 140 }
]

const buildParams = () => ({
  current: page.value,
  size: pageSize.value,
  messageNo: filters.messageNo || undefined,
  status: filters.status || undefined,
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: empty string = no type filter
  messageType: filters.messageType || undefined,
  startDate: filters.startDate || undefined,
  endDate: filters.endDate || undefined,
  inDeadLetter: filters.inDeadLetter === 'yes' ? true : filters.inDeadLetter === 'no' ? false : undefined,
  ruleName: filters.ruleName || undefined,
  channelName: filters.channelName || undefined
})

const fetch = async () => {
  loading.value = true
  try {
    const res = await request.get('/alerts/history', { params: buildParams() })
    data.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  fetch()
}

// DatePicker on-change emits the formatted (yyyy-MM-dd) string; auto-search on pick/clear
const setStartDate = (v) => {
  filters.startDate = v
  search()
}

const setEndDate = (v) => {
  filters.endDate = v
  search()
}

const reset = () => {
  filters.messageNo = ''
  filters.status = ''
  filters.messageType = ''
  filters.startDate = ''
  filters.endDate = ''
  filters.inDeadLetter = ''
  filters.ruleName = ''
  filters.channelName = ''
  search()
}

const onPageChange = (p) => {
  page.value = p
  fetch()
}

const onPageSizeChange = (size) => {
  pageSize.value = size
  page.value = 1
  fetch()
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
  try {
    const res = await gatewayRequest.post(`/alerts/dead-letters/retry-by-message/${row.id}`)
    Message.success({ content: `已重新入队，新告警 ID：${res.data?.messageId || '--'}` })
    fetch()
  } catch (e) {
    Message.error({ content: e.response?.data?.error || '重试失败' })
  }
}

const msBetween = (a, b) => {
  if (!a || !b) return '--'
  const t1 = new Date(a).getTime()
  const t2 = new Date(b).getTime()
  if (isNaN(t1) || isNaN(t2) || t2 < t1) return '--'
  return (t2 - t1) + 'ms'
}

const formatContent = (row) => prettyJson(row.payload || '')

const fmtRawPayload = computed(() => {
  const raw = detail.value?.rawPayload
  return raw ? prettyJson(raw) : '（无原始报文记录）'
})

const fmtPayload = computed(() => {
  const p = detail.value?.payload
  return p ? prettyJson(p) : '（无告警内容）'
})

const copyText = async (text, label) => {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    Message.success({ content: label + '已复制' })
  } catch (e) {
    Message.error({ content: '复制失败' })
  }
}

const gwMs = computed(() => detail.value ? msBetween(detail.value.receivedAt, detail.value.gwSentAt) : '--')
const mqMs = computed(() => detail.value ? msBetween(detail.value.gwSentAt, detail.value.workerReceivedAt) : '--')
const workerMs = computed(() => detail.value ? msBetween(detail.value.workerReceivedAt, detail.value.workerSentAt) : '--')
const finishMs = computed(() => detail.value ? msBetween(detail.value.workerSentAt, detail.value.finishedAt) : '--')

onMounted(fetch)
</script>

<style scoped>
.table-card { border-radius: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--aw-text); }
.pager { margin-top: 12px; text-align: right; }

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.filter-item { width: 150px; }
.date-item { width: 200px; }

.summary-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: Consolas, monospace;
  font-size: 12px;
  color: var(--aw-text-secondary);
}

.channel-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--aw-text-secondary);
}

.dim { color: var(--aw-text-faint); }
.action-group { display: flex; gap: 6px; }

.status-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;
}
.st-success { color: var(--aw-success); background: rgba(110, 185, 138, 0.16); }
.st-info { color: var(--aw-info); background: rgba(127, 180, 201, 0.16); }
/* #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: status of an alert dropped by a silenced rule */
.st-silenced { color: #9b8fd0; background: rgba(155, 143, 208, 0.16); }
/* #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: status of an alert dropped because no rule matched */
.st-unmatched { color: var(--aw-info); background: rgba(127, 180, 201, 0.16); }
/* #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: stored for the daily report instead of being sent */
.st-report-pending { color: #9b8fd0; background: rgba(155, 143, 208, 0.16); }
.st-failed {
  color: #fff;
  background: var(--aw-error);
  font-weight: 700;
}

.detail { position: relative; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.detail-item {
  font-size: 14px;
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 10px 12px;
}
.detail-item label { display: block; font-size: 13px; color: var(--aw-text-faint); margin-bottom: 4px; }
.detail-item .mono { font-family: Consolas, monospace; color: var(--aw-accent); }
.archive-link { color: var(--aw-accent); text-decoration: underline; }
.trace-title, .raw-title { font-size: 13px; font-weight: 600; color: var(--aw-text); margin: 18px 0 10px; }
.raw-title-row { display: flex; align-items: center; justify-content: space-between; margin: 18px 0 10px; }
.raw-title-row .raw-title { margin: 0; }
.trace { display: flex; align-items: flex-start; flex-wrap: wrap; }
.trace-node { display: flex; flex-direction: column; align-items: center; width: 92px; }
.trace-dot { width: 10px; height: 10px; border-radius: 50%; background: var(--aw-text-faint); border: 2px solid var(--aw-card); }
.trace-dot.on { background: var(--aw-success); box-shadow: 0 0 8px var(--aw-primary-glow); }
.trace-label { font-size: 13px; color: var(--aw-text-secondary); margin-top: 6px; text-align: center; }
.trace-time { font-size: 12px; white-space: nowrap; color: var(--aw-text-faint); margin-top: 2px; }
.trace-seg { flex: 1; min-width: 60px; height: 2px; background: var(--aw-border); margin-top: 6px; position: relative; display: flex; justify-content: center; }
.trace-seg.done { background: var(--aw-primary); }
.trace-seg-text { position: absolute; top: 10px; font-size: 11px; color: var(--aw-text-secondary); background: var(--aw-card); padding: 0 6px; white-space: nowrap; }
/* #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: flat placeholder for rows that never entered the MQ */
.trace-flat {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 12px 14px;
}
.trace-flat-node { display: flex; flex-direction: column; align-items: center; gap: 4px; min-width: 78px; }
.trace-flat-label { font-size: 13px; color: var(--aw-text-secondary); }
.trace-flat-dash { font-size: 12px; color: var(--aw-text-faint); }
.trace-flat .form-tip { margin: 0; width: 100%; font-size: 12px; color: var(--aw-text-faint); }
.raw-payload {
  background: var(--aw-bg-deep);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 14px;
  font-size: 13px;
  color: var(--aw-text-secondary);
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
