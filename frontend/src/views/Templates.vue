// #AI-CODE-FILE-dev_release/v0.0.1-20260901: unify theme + Chinese UI + remove channel binding + HTML preview
<template>
  <div>
    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">消息模板</span>
        <Button type="primary" size="small" @click="openCreateModal">
          <Icon type="ios-add" /> 新建模板
        </Button>
      </div>
      <Table :columns="columns" :data="data" :loading="loading">
        <template #action="{ row }">
          <Button size="small" @click="openPreview(row)">预览</Button>
          <Button size="small" @click="edit(row)">编辑</Button>
          <Button size="small" type="error" :disabled="row.builtin === true" @click="remove(row.id)">删除</Button>
        </template>
      </Table>
      <div class="pager">
        <Page :total="total" :page-size="pageSize" :current="page" show-sizer :page-size-opts="[10, 15, 20, 50, 100]" @on-change="onPageChange" @on-page-size-change="onPageSizeChange" />
      </div>
    </Card>

    <Modal v-model="modalVisible" :title="modalTitle" @on-ok="handleSubmit">
      <Form :model="formData" :label-width="90">
        <FormItem label="名称">
          <Input v-model="formData.name" placeholder="模板名称" />
        </FormItem>
        <FormItem label="格式">
          <Select v-model="formData.format" style="width: 220px">
            <Option value="TEXT">文本（内联发送）</Option>
            <Option value="HTML">HTML（生成文件并上传对象存储）</Option>
          </Select>
          <div class="form-tip">HTML 模板用于日报：渲染成自包含 HTML 文件上传对象存储，邮件内嵌、钉钉发链接</div>
        </FormItem>
        <FormItem label="模板内容">
          <Input v-model="formData.content" type="textarea" :rows="7" placeholder="Jinjava 模板内容，支持 {{ labels.xxx }} 等变量" />
          <div class="form-tip">模板不绑定渠道，发送渠道由规则绑定时指定（钉钉/邮件）</div>
        </FormItem>
        <FormItem label="默认模板">
          <Checkbox v-model="formData.isDefault">设为默认模板（无渠道模板时的兜底）</Checkbox>
        </FormItem>
      </Form>
    </Modal>

    <Modal v-model="previewVisible" title="模板预览" width="760">
      <div class="preview-layout">
        <div class="preview-context">
          <div class="preview-label">渲染上下文（JSON，可编辑）<span class="preview-kind">{{ previewKind }}</span></div>
          <Input v-model="previewContext" type="textarea" :rows="12" class="mono-input" />
        </div>
        <div class="preview-result">
          <div class="preview-label">渲染结果</div>
          <div v-if="isHtmlContent" class="preview-html" v-html="previewContent"></div>
          <pre v-else class="preview-render">{{ previewContent || '（点击渲染后显示）' }}</pre>
        </div>
      </div>
      <template #footer>
        <Button @click="previewVisible = false">关闭</Button>
        <Button type="primary" :loading="previewing" @click="doPreview">渲染</Button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Modal, Message, Tag } from 'view-ui-plus'
import request from '../api/request.js'
import { isHtml } from '../utils/json'
import { fmtTime } from '../utils/time.js'

const loading = ref(false)
const data = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)
const modalVisible = ref(false)
const modalTitle = ref('新建模板')
const editingId = ref(null)

const previewVisible = ref(false)
const previewing = ref(false)
const previewingId = ref(null)
const previewContext = ref('')
const previewContent = ref('')
const previewKind = ref('告警上下文')

const formData = reactive({ name: '', content: '', isDefault: false, format: 'TEXT' })

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: 默认预览上下文 = Alertmanager webhook 报文结构
// （网关会原样透传并拍平首条告警的 labels/annotations，Worker 再注入 recipient/messageNo），
// 与线上渲染条件一致，{% for alert in alerts %} 类模板可直接预览
const isoAgo = (ms) => new Date(Date.now() - ms).toISOString()

const SAMPLE_CONTEXT = {
  version: '4',
  groupKey: 'alertname=HighCPU',
  status: 'firing',
  receiver: 'alertworker',
  groupLabels: { alertname: 'HighCPU' },
  commonLabels: { alertname: 'HighCPU', severity: 'warning', instance: '10.0.0.1:9100' },
  commonAnnotations: {},
  externalURL: 'http://alertmanager.example.com:9093',
  messageNo: 'DEMO-20260916-0001',
  messageId: 'DEMO-20260916-0001',
  labels: { alertname: 'HighCPU', severity: 'warning', instance: '10.0.0.1:9100', service_type: 'core' },
  annotations: {
    summary: 'CPU 使用率过高',
    description: '实例 CPU 使用率超过 90%',
    runbook_url: 'http://runbook.example.com/cpu-high'
  },
  alerts: [
    {
      status: 'firing',
      labels: { alertname: 'HighCPU', severity: 'warning', instance: '10.0.0.1:9100', service_type: 'core' },
      annotations: {
        summary: 'CPU 使用率过高',
        description: '实例 CPU 使用率超过 90%',
        runbook_url: 'http://runbook.example.com/cpu-high'
      },
      startsAt: isoAgo(30 * 60 * 1000),
      endsAt: '0001-01-01T00:00:00Z',
      generatorURL: 'http://prometheus.example.com:9090/graph',
      fingerprint: 'abc123def'
    }
  ],
  recipient: { cnName: '张三', phone: '13800000000', dingdingUsername: 'zhangsan', channelType: 'DINGDING' }
}

// #AI-CODE-FUNCTION-dev_release/v0.0.2-20261009: 日报模板上下文 = DailyReportJobExecutor.buildReportContext 的结构
// （date/totalCount/byRule/bySeverity/byServiceType/alerts），供内置日报模板（TEXT/HTML）预览时填入真实字段
const REPORT_SAMPLE_CONTEXT = {
  title: '告警日报',
  date: new Date().toISOString().slice(0, 10),
  generatedAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
  totalCount: 3,
  byRule: [
    { ruleId: 1, ruleName: '核心服务告警', count: 2 },
    { ruleId: 2, ruleName: '主机资源告警', count: 1 }
  ],
  bySeverity: [
    { severity: 'warning', count: 2 },
    { severity: 'critical', count: 1 }
  ],
  byServiceType: [
    { serviceType: 'core', count: 2 },
    { serviceType: 'db', count: 1 }
  ],
  // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: WARNING 汇总模板还使用 byAssignee（责任人接收条数）
  byAssignee: [
    { assignee: '张三', count: 2 },
    { assignee: '李四', count: 1 }
  ],
  trends: [
    {
      ruleId: 1,
      ruleName: '核心服务告警',
      total: 2,
      avg: 0.08,
      median: 0,
      p20: 0,
      p40: 0,
      p60: 0,
      p80: 0,
      max: 1,
      min: 0,
      series: [0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0]
    },
    {
      ruleId: 2,
      ruleName: '主机资源告警',
      total: 1,
      avg: 0.04,
      median: 0,
      p20: 0,
      p40: 0,
      p60: 0,
      p80: 0,
      max: 1,
      min: 0,
      series: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1]
    }
  ],
  alerts: [
    {
      messageNo: 'DEMO-20261009-0001',
      ruleName: '核心服务告警',
      severity: 'warning',
      serviceType: 'core',
      createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString().slice(0, 19),
      summary: 'CPU 使用率过高',
      content: '实例 web-01 CPU 使用率超过 90%',
      assignees: '张三、李四'
    },
    {
      messageNo: 'DEMO-20261009-0002',
      ruleName: '核心服务告警',
      severity: 'critical',
      serviceType: 'core',
      createdAt: new Date(Date.now() - 15 * 60 * 1000).toISOString().slice(0, 19),
      summary: '接口错误率超过阈值',
      content: '订单服务接口错误率 5 分钟内超过 5%',
      assignees: '张三'
    },
    {
      messageNo: 'DEMO-20261009-0003',
      ruleName: '主机资源告警',
      severity: 'warning',
      serviceType: 'db',
      createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString().slice(0, 19),
      summary: '磁盘使用率超过 85%',
      content: '数据库 db-02 磁盘使用率达到 88%',
      assignees: '李四'
    }
  ]
}

// 日报模板的判别：内容引用了日报聚合上下文的变量（内置/自定义日报模板均适用）
const REPORT_CONTEXT_TOKENS = ['byRule', 'bySeverity', 'byServiceType', 'totalCount']
const isReportTemplate = (content) => REPORT_CONTEXT_TOKENS.some((t) => (content || '').includes(t))

const columns = [
  {
    title: '名称',
    key: 'name',
    render: (h, p) => h('span', {}, [
      p.row.name,
      // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261009: builtin templates are system-owned (editable, not deletable)
      p.row.builtin === true
        ? h(Tag, { color: 'purple', style: 'margin-left: 8px' }, () => '内置')
        : null
    ])
  },
  {
    title: '格式', key: 'format', width: 90, align: 'center',
    render: (h, p) => h('span', {}, p.row.format === 'HTML' ? 'HTML' : '文本')
  },
  {
    title: '默认', key: 'isDefault', width: 80, align: 'center',
    render: (h, p) => h('span', {}, p.row.isDefault ? '是' : '—')
  },
  { title: '创建时间', key: 'createdAt', width: 170,
    render: (h, p) => h('span', {}, fmtTime(p.row.createdAt)) },
  { title: '操作', slot: 'action', width: 190 }
]

const isHtmlContent = computed(() => isHtml(previewContent.value))

const fetch = async () => {
  loading.value = true
  try {
    const res = await request.get('/templates', { params: { current: page.value, size: pageSize.value } })
    data.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
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

const openPreview = (row) => {
  previewingId.value = row.id
  const isReport = isReportTemplate(row.content)
  previewKind.value = isReport ? '日报上下文' : '告警上下文'
  previewContext.value = JSON.stringify(isReport ? REPORT_SAMPLE_CONTEXT : SAMPLE_CONTEXT, null, 2)
  previewContent.value = ''
  previewVisible.value = true
}

const doPreview = async () => {
  let context
  try {
    context = JSON.parse(previewContext.value)
  } catch (e) {
    Message.error({ content: '上下文不是合法的 JSON' })
    return
  }
  previewing.value = true
  try {
    const res = await request.post(`/templates/${previewingId.value}/preview`, context)
    previewContent.value = typeof res.data === 'string' ? res.data : JSON.stringify(res.data)
  } catch (e) {
    Message.error({ content: '渲染失败' })
  } finally {
    previewing.value = false
  }
}

const remove = async (id) => {
  const row = data.value.find(x => x.id === id)
  if (row && row.builtin === true) {
    Message.warning({ content: '内置模板不可删除' })
    return
  }
  Modal.confirm({
    title: '删除确认',
    content: '确定删除该模板吗？此操作不可撤销。',
    onOk: async () => {
      await request.delete(`/templates/${id}`)
      Message.success({ content: '已删除' })
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = '新建模板'
  editingId.value = null
  formData.name = ''
  formData.content = ''
  formData.isDefault = false
  formData.format = 'TEXT'
  modalVisible.value = true
}

const edit = (row) => {
  modalTitle.value = '编辑模板'
  editingId.value = row.id
  formData.name = row.name
  formData.content = row.content || ''
  formData.isDefault = row.isDefault || false
  formData.format = row.format === 'HTML' ? 'HTML' : 'TEXT'
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (!formData.name?.trim()) {
    Message.warning({ content: '请填写模板名称' })
    return
  }
  if (!formData.content?.trim()) {
    Message.warning({ content: '请填写模板内容' })
    return
  }
  if (editingId.value) {
    await request.put(`/templates/${editingId.value}`, formData)
  } else {
    await request.post('/templates', formData)
  }
  modalVisible.value = false
  Message.success({ content: '已保存' })
  fetch()
}

onMounted(fetch)
</script>

<style scoped>
.table-card { border-radius: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--aw-text); }
.form-tip { margin-top: 4px; font-size: 12px; color: var(--aw-text-faint); }
.pager { margin-top: 12px; text-align: right; }

.preview-layout { display: flex; flex-direction: column; gap: 16px; }
.preview-label { font-size: 13px; font-weight: 600; color: var(--aw-text); margin-bottom: 6px; }
.preview-kind {
  margin-left: 8px;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
  background: var(--aw-panel, #eef1f6);
  color: var(--aw-text-secondary, #515a6e);
}
.mono-input :deep(textarea) { font-family: Consolas, monospace; font-size: 12px; }
.preview-render {
  background: var(--aw-bg-deep);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 12px;
  font-size: 12px;
  color: var(--aw-text-secondary);
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.preview-html {
  background: #ffffff;
  border: 1px solid var(--aw-border);
  border-radius: 8px;
  padding: 12px;
  max-height: 320px;
  overflow: auto;
  color: #1f2d3d;
}
</style>
