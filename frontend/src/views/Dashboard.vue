<template>
  <div class="dashboard">
    <div class="dashboard-toolbar">
      <span class="toolbar-title">仪表盘 · 24 小时告警概览</span>
      <div class="toolbar-right">
        <Button size="small" :loading="refreshing" @click="manualRefresh">
          <Icon type="ios-refresh" /> 刷新
        </Button>
        <Icon type="ios-time-outline" size="15" />
        <span class="toolbar-label">自动刷新</span>
        <Select v-model="refreshInterval" filterable size="small" class="interval-select">
          <Option :value="10">10 秒</Option>
          <Option :value="20">20 秒</Option>
          <Option :value="40">40 秒</Option>
          <Option :value="60">60 秒</Option>
        </Select>
      </div>
    </div>
    <Row :gutter="16">
      <Col span="6">
        <Card class="stat-card" :bordered="false">
          <div class="stat-icon total"><Icon type="ios-pulse" size="20" /></div>
          <div class="stat-info">
            <div class="stat-title">24h 总消息</div>
            <div class="stat-value">{{ stats.total }}</div>
          </div>
        </Card>
      </Col>
      <Col span="6">
        <Card class="stat-card" :bordered="false">
          <div class="stat-icon success"><Icon type="ios-checkmark-circle-outline" size="20" /></div>
          <div class="stat-info">
            <div class="stat-title">成功</div>
            <div class="stat-value success">{{ stats.success }}</div>
          </div>
        </Card>
      </Col>
      <Col span="6">
        <Card class="stat-card" :bordered="false">
          <div class="stat-icon error"><Icon type="ios-close-circle-outline" size="20" /></div>
          <div class="stat-info">
            <div class="stat-title">失败</div>
            <div class="stat-value error">{{ stats.failed }}</div>
          </div>
        </Card>
      </Col>
      <Col span="6">
        <Card class="stat-card" :bordered="false">
          <div class="stat-icon warning"><Icon type="ios-bulb-outline" size="20" /></div>
          <div class="stat-info">
            <div class="stat-title">死信</div>
            <div class="stat-value warning">{{ stats.deadLetters }}</div>
          </div>
        </Card>
      </Col>
    </Row>

    <Card :bordered="false" class="panel-card">
      <div class="panel-header">
        <span class="panel-title">24 小时告警趋势</span>
        <span class="panel-sub">最近 24 小时 · 总消息 / 失败 / 死信</span>
      </div>
      <LineChart :labels="chart.labels" :series="chart.series" :height="280" />
    </Card>

    <Row :gutter="16" class="timing-row">
      <Col span="8">
        <Card :bordered="false" class="timing-card">
          <div class="panel-title">Gateway 处理耗时</div>
          <div class="panel-sub">接收告警 → 写入 MQ</div>
          <TimingStat :segment="timing.gatewayProcessMs" />
        </Card>
      </Col>
      <Col span="8">
        <Card :bordered="false" class="timing-card">
          <div class="panel-title">队列转运耗时</div>
          <div class="panel-sub">Gateway → Worker（含 MQ 排队）</div>
          <TimingStat :segment="timing.mqTransitMs" />
        </Card>
      </Col>
      <Col span="8">
        <Card :bordered="false" class="timing-card">
          <div class="panel-title">Worker 派发耗时</div>
          <div class="panel-sub">Worker 接收 → 外部系统发送</div>
          <TimingStat :segment="timing.workerProcessMs" />
        </Card>
      </Col>
    </Row>

    <Card :bordered="false" class="panel-card">
      <div class="panel-header">
        <span class="panel-title">Recent Alerts</span>
        <span class="panel-sub">最近 10 条 · 告警内容 / 耗时 / 状态</span>
      </div>
      <Table :columns="columns" :data="recentAlerts" size="small" :loading="loading">
        <template #content="{ row }">
          <Tooltip :content="formatContent(row)" placement="top" :max-width="560" transfer>
            <span class="summary-cell">{{ row.summary }}</span>
          </Tooltip>
        </template>
      </Table>
    </Card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, defineComponent, h, watch } from 'vue'
import request from '../api/request.js'
import LineChart from '../components/LineChart.vue'
import { prettyJson } from '../utils/json'

const stats = ref({ total: 0, success: 0, failed: 0, deadLetters: 0 })
const recentAlerts = ref([])
const timing = ref({ gatewayProcessMs: {}, mqTransitMs: {}, workerProcessMs: {} })
const loading = ref(false)
const chart = ref({ labels: [], series: [] })

const TimingStat = defineComponent({
  name: 'TimingStat',
  props: { segment: { type: Object, default: () => ({}) } },
  setup (props) {
    return () => h('div', { class: 'timing-grid' }, [
      h('div', { class: 'timing-item' }, [
        h('div', { class: 'timing-label' }, '平均'),
        h('div', { class: 'timing-value' }, fmt(props.segment.avg))
      ]),
      h('div', { class: 'timing-item' }, [
        h('div', { class: 'timing-label' }, 'P95'),
        h('div', { class: 'timing-value accent' }, fmt(props.segment.p95))
      ]),
      h('div', { class: 'timing-item' }, [
        h('div', { class: 'timing-label' }, '最大'),
        h('div', { class: 'timing-value warn' }, fmt(props.segment.max))
      ])
    ])
  }
})

const fmt = (v) => {
  if (v == null) return '--'
  if (v >= 1000) return (v / 1000).toFixed(2) + ' s'
  return Math.round(v) + ' ms'
}

const formatContent = (row) => prettyJson(row.fullContent || row.summary || '')

const statusTag = (status) => {
  const map = {
    success: { color: 'green', text: '成功' },
    WORKER_SENT: { color: 'green', text: '已发送' },
    DELIVERED: { color: 'blue', text: '处理中' },
    GW_SENT: { color: 'blue', text: '已入队' },
    recieved: { color: 'blue', text: '已接收' },
    FAILED: { color: 'red', text: '失败' }
  }
  const m = map[status] || { color: 'default', text: status || '-' }
  return h('Tag', { color: m.color }, () => m.text)
}

const columns = [
  { title: '告警 ID', key: 'messageNo', minWidth: 190 },
  { title: '告警内容', slot: 'content', minWidth: 340 },
  {
    title: '耗时', key: 'totalMs', width: 110,
    render: (h, params) => {
      const ms = params.row.totalMs
      const text = ms == null ? '--' : (ms >= 1000 ? (ms / 1000).toFixed(2) + 's' : ms + 'ms')
      return h('span', { class: ms != null && ms > 5000 ? 'duration slow' : 'duration' }, text)
    }
  },
  {
    title: '状态', key: 'status', width: 110,
    render: (h, params) => statusTag(params.row.status)
  },
  { title: '时间', key: 'createdAt', width: 170 }
]

const fetchStats = async () => {
  try {
    const [overview, recent, timingRes, hourly] = await Promise.all([
      request.get('/stats/overview'),
      request.get('/alerts/recent?size=10'),
      request.get('/stats/timing'),
      request.get('/stats/24h')
    ])
    stats.value = overview.data
    timing.value = timingRes.data
    recentAlerts.value = recent.data || []

    const buckets = hourly.data || []
    chart.value = {
      labels: buckets.map(b => {
        const t = b.hour.split('T')[1]?.slice(0, 5) || b.hour
        return t
      }),
      series: [
        { name: '总消息', color: '#7fbf9a', data: buckets.map(b => b.total) },
        { name: '失败', color: '#d97a7a', data: buckets.map(b => b.failed) },
        { name: '死信', color: '#d0ae6e', data: buckets.map(b => b.deadLetters) }
      ]
    }
  } catch (e) {
    console.error(e)
  }
}

const refreshInterval = ref(20)
const refreshing = ref(false)
let timer = null

const startTimer = () => {
  if (timer) clearInterval(timer)
  timer = setInterval(fetchStats, refreshInterval.value * 1000)
}

const manualRefresh = async () => {
  refreshing.value = true
  try {
    await fetchStats()
  } finally {
    refreshing.value = false
  }
}

watch(refreshInterval, () => {
  startTimer()
  fetchStats()
})

onMounted(() => {
  fetchStats()
  startTimer()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.dashboard { max-width: 1440px; margin: 0 auto; }

.dashboard-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--aw-text);
  letter-spacing: 0.5px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--aw-text-secondary);
}

.toolbar-right .ivu-icon { color: var(--aw-text-faint); }

.interval-select { width: 90px; }

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  border-radius: 12px;
  padding: 6px 0;
  background: var(--aw-card);
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-icon.total { background: rgba(127, 191, 154, 0.14); color: var(--aw-accent); }
.stat-icon.success { background: rgba(111, 185, 138, 0.14); color: var(--aw-success); }
.stat-icon.error { background: rgba(217, 122, 122, 0.14); color: var(--aw-error); }
.stat-icon.warning { background: rgba(208, 174, 110, 0.14); color: var(--aw-warning); }

.stat-title { font-size: 13px; color: var(--aw-text-secondary); margin-bottom: 2px; }
.stat-value { font-size: 26px; font-weight: 600; color: var(--aw-text); line-height: 1.1; }
.stat-value.success { color: var(--aw-success); }
.stat-value.error { color: var(--aw-error); }
.stat-value.warning { color: var(--aw-warning); }

.panel-card { margin-top: 16px; border-radius: 12px; }
.panel-header { margin-bottom: 6px; }
.panel-title { font-size: 15px; font-weight: 600; color: var(--aw-text); letter-spacing: 0.5px; }
.panel-sub { font-size: 12px; color: var(--aw-text-faint); margin-left: 12px; }

.timing-row { margin-top: 16px; }
.timing-card { border-radius: 12px; }

.timing-grid {
  display: flex;
  gap: 12px;
  margin-top: 14px;
}

.timing-item {
  flex: 1;
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 10px;
  padding: 12px;
  text-align: center;
}

.timing-label { font-size: 12px; color: var(--aw-text-faint); margin-bottom: 4px; }
.timing-value { font-size: 20px; font-weight: 600; color: var(--aw-text); }
.timing-value.accent { color: var(--aw-accent); }
.timing-value.warn { color: var(--aw-warning); }

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

.duration { color: var(--aw-text-secondary); font-variant-numeric: tabular-nums; }
.duration.slow { color: var(--aw-warning); font-weight: 600; }
</style>
