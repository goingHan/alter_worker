// #AI-CODE-FILE-dev_release/v0.0.1-20261008: daily-report config page — list + create/edit modal with rule/recipient/channel sub-resources
<template>
  <div>
    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">告警日报</span>
        <Button type="primary" size="small" @click="openCreateModal">
          <Icon type="ios-add" /> 新建日报
        </Button>
      </div>
      <Table :columns="columns" :data="data" :loading="loading">
        <template #action="{ row }">
          <Button size="small" @click="edit(row)">编辑</Button>
          <Button size="small" type="primary" @click="runNow(row)">立即生成</Button>
          <Button size="small" type="error" @click="remove(row.id)">删除</Button>
        </template>
      </Table>
      <div class="pager">
        <Page :total="total" :page-size="pageSize" :current="page" show-sizer :page-size-opts="[10, 15, 20, 50, 100]" @on-change="onPageChange" @on-page-size-change="onPageSizeChange" />
      </div>
    </Card>

    <Modal v-model="modalVisible" :title="modalTitle" width="960" @on-ok="handleSubmit">
      <Form :model="formData" :label-width="90">
        <FormItem label="名称">
          <Input v-model="formData.name" placeholder="日报名称" />
        </FormItem>
        <FormItem label="Cron 表达式">
          <Input v-model="formData.cronExpr" placeholder="0 5 8 * * *" />
        </FormItem>
        <FormItem label="统计时间">
          <TimePicker v-model="formData.statStartTime" type="time" format="HH:mm" placeholder="起始 00:00" style="width: 130px" />
          <span class="range-sep">至</span>
          <TimePicker v-model="formData.statEndTime" type="time" format="HH:mm" placeholder="结束 23:59" style="width: 130px" />
          <div class="form-tip">统计当天的告警时间段，范围 00:00–23:59；留空表示统计全天</div>
        </FormItem>
        <FormItem label="启用">
          <i-switch v-model="formData.enabled" />
        </FormItem>
        <FormItem label="模板">
          <Select v-model="formData.templateId" clearable filterable placeholder="选择渲染模板">
            <Option v-for="t in allTemplates" :key="t.id" :value="t.id">{{ t.name }}{{ t.format === 'HTML' ? '（HTML）' : '' }}</Option>
          </Select>
        </FormItem>
        <FormItem label="关联规则">
          <Transfer
            class="rule-transfer"
            :data="ruleTransferData"
            :target-keys="formRuleIds"
            :list-style="{ width: '380px', height: '320px' }"
            filterable
            filter-placeholder="按规则名称筛选"
            :titles="['未选规则', '已选规则']"
            @on-change="onRuleTransferChange"
          />
          <div class="form-tip">日报聚合所选规则当日的告警（含已发送与「已入库待日报」，每条只计入一次）</div>
        </FormItem>
        <FormItem label="接收人">
          <div class="recipient-list">
            <div class="recipient-add">
              <Select v-model="addRecipient.assigneeId" filterable placeholder="选择人员" style="width: 180px">
                <Option v-for="a in allAssignees" :key="a.id" :value="a.id">{{ a.cnName }}</Option>
              </Select>
              <Select v-model="addRecipient.channelType" style="width: 110px">
                <Option value="DINGDING">钉钉</Option>
                <Option value="EMAIL">邮件</Option>
              </Select>
              <Button size="small" type="primary" @click="addRecipientRow">添加</Button>
            </div>
            <div v-if="recipientRows.length" class="recipient-rows">
              <div v-for="(r, i) in recipientRows" :key="i" class="recipient-row">
                <span class="member-name">{{ recipientName(r.assigneeId) }}（{{ channelText(r.channelType) }}）</span>
                <Button size="small" type="error" @click="removeRecipientRow(i)">删除</Button>
              </div>
            </div>
            <div v-else class="member-empty">接收人可留空：钉钉渠道将只发送到群聊、不 @ 任何人；邮件渠道需配置接收人邮箱</div>
          </div>
          <div class="form-tip">有接收人时钉钉会 @ 对应的人；接收人为空时仅按「关联渠道」发送、不 @ 任何人</div>
        </FormItem>
        <FormItem label="关联渠道">
          <Select v-model="formChannelIds" multiple filterable placeholder="选择发送渠道">
            <Option v-for="c in allChannels" :key="c.id" :value="c.id">{{ c.name }}（{{ channelTypeText(c.channelType) }}）</Option>
          </Select>
        </FormItem>
        <FormItem label="趋势监控项">
          <Transfer
            class="rule-transfer"
            :data="ruleTransferData"
            :target-keys="formTrendRuleIds"
            :list-style="{ width: '380px', height: '260px' }"
            filterable
            filter-placeholder="按规则名称筛选"
            :titles="['未选规则', '已选规则']"
            @on-change="onTrendTransferChange"
          />
          <div class="form-tip">按已选规则统计当天每小时（0–23）告警数的 平均 / 中位数 / P20 / P40 / P60 / P80 / 最大 / 最小</div>
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, h } from 'vue'
import { Modal, Message, Tag, Transfer } from 'view-ui-plus'
import request, { processorRequest } from '../api/request.js'
import { fmtTime } from '../utils/time.js'

const loading = ref(false)
const data = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)
const modalVisible = ref(false)
const modalTitle = ref('新建日报')
const editingId = ref(null)

const allTemplates = ref([])
const allRules = ref([])
const allAssignees = ref([])
const allChannels = ref([])

const formData = reactive({ name: '', cronExpr: '', enabled: false, templateId: null, statStartTime: '', statEndTime: '' })
const formRuleIds = ref([])
const formChannelIds = ref([])
const recipientRows = ref([])
// #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: trend monitoring items = rules selected for per-rule trend stats
const formTrendRuleIds = ref([])
const addRecipient = reactive({ assigneeId: null, channelType: 'DINGDING' })
// sub-resource snapshots loaded on edit; the save handler diffs the edited set against these
const originalRecipients = ref([])
const originalRulesOfEdit = ref([])
const originalChannelIdsOfEdit = ref([])
const originalTrendRuleIds = ref([])

const alertTypeText = (t) => (t === 'JSON' ? 'JSON' : t === 'PROMETHEUS' ? 'Prometheus' : (t || '--'))
const channelTypeText = (t) => (t === 'EMAIL' ? '邮件' : t === 'DINGDING' ? '钉钉' : (t || '--'))
const channelText = (t) => (t === 'EMAIL' ? '邮件' : '钉钉')

// #AI-CODE-FUNCTION-dev_release/v0.0.2-20261012: LocalTime comes back as "HH:mm:ss"; the TimePicker uses "HH:mm"
const hhmm = (t) => (t ? String(t).slice(0, 5) : '')
const statRangeText = (row) => {
  const s = hhmm(row.statStartTime)
  const e = hhmm(row.statEndTime)
  if (!s && !e) return '全天'
  return `${s || '00:00'} - ${e || '23:59'}`
}

const templateName = (id) => {
  if (!id) return '--'
  const t = allTemplates.value.find(x => x.id === id)
  return t ? t.name : String(id)
}

const recipientName = (id) => {
  const a = allAssignees.value.find(x => x.id === id)
  return a ? a.cnName : String(id)
}

// #AI-CODE-FUNCTION-dev_release/v0.0.2-20261008: a daily report may aggregate any rule's alerts (both normally
// delivered alerts and REPORT_PENDING rows held by report-only rules), so the picker lists every rule.
const ruleTransferData = computed(() => allRules.value.map(r => ({
  key: r.id,
  label: `${r.name}（${alertTypeText(r.alertType)}）`
})))

const onRuleTransferChange = (keys) => {
  formRuleIds.value = keys
}

const onTrendTransferChange = (keys) => {
  formTrendRuleIds.value = keys
}

const columns = [
  { title: '名称', key: 'name' },
  { title: 'Cron 表达式', key: 'cronExpr', width: 150, render: (h, p) => h('span', { class: 'mono-cell' }, p.row.cronExpr || '--') },
  { title: '统计时间', key: 'statRange', width: 140, render: (h, p) => h('span', { class: 'mono-cell' }, statRangeText(p.row)) },
  { title: '模板', key: 'templateId', width: 140, render: (h, p) => h('span', {}, templateName(p.row.templateId)) },
  {
    title: '启用',
    key: 'enabled',
    width: 80,
    align: 'center',
    render: (h, p) => h(Tag, { color: p.row.enabled ? 'green' : 'default' }, () => (p.row.enabled ? '启用' : '停用'))
  },
  { title: '创建时间', key: 'createdAt', width: 170,
    render: (h, p) => h('span', {}, fmtTime(p.row.createdAt)) },
  { title: '操作', slot: 'action', width: 220 }
]

const fetch = async () => {
  loading.value = true
  try {
    const res = await request.get('/daily-reports', { params: { current: page.value, size: pageSize.value } })
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

const loadOptions = async () => {
  try {
    const [templatesRes, rulesRes, assigneesRes, channelsRes] = await Promise.all([
      request.get('/templates', { params: { current: 1, size: 1000 } }),
      request.get('/rules', { params: { current: 1, size: 1000 } }),
      request.get('/assignees', { params: { current: 1, size: 1000 } }),
      request.get('/channels', { params: { current: 1, size: 1000 } })
    ])
    allTemplates.value = templatesRes.data?.records || []
    allRules.value = rulesRes.data?.records || []
    allAssignees.value = assigneesRes.data?.records || []
    allChannels.value = channelsRes.data?.records || []
  } catch (e) {
    console.error(e)
  }
}

const fetchSubResources = async (id) => {
  const [rulesRes, recipientsRes, channelsRes, trendRes] = await Promise.all([
    request.get(`/daily-reports/${id}/rules`),
    request.get(`/daily-reports/${id}/recipients`),
    request.get(`/daily-reports/${id}/channels`),
    request.get(`/daily-reports/${id}/trend-rules`)
  ])
  formRuleIds.value = (rulesRes.data || []).map(x => x.ruleId)
  originalRulesOfEdit.value = rulesRes.data || []
  originalRecipients.value = recipientsRes.data || []
  recipientRows.value = originalRecipients.value.map(m => ({
    id: m.id,
    assigneeId: m.assigneeId,
    channelType: m.channelType
  }))
  formChannelIds.value = (channelsRes.data || []).map(x => x.channelId)
  originalChannelIdsOfEdit.value = channelsRes.data || []
  originalTrendRuleIds.value = trendRes.data || []
  formTrendRuleIds.value = originalTrendRuleIds.value.map(x => x.ruleId)
}

const addRecipientRow = () => {
  if (!addRecipient.assigneeId) {
    Message.warning({ content: '请选择人员' })
    return
  }
  const exists = recipientRows.value.some(r => r.assigneeId === addRecipient.assigneeId && r.channelType === addRecipient.channelType)
  if (exists) {
    Message.warning({ content: '该人员已在接收人中（相同渠道）' })
    return
  }
  recipientRows.value.push({ id: null, assigneeId: addRecipient.assigneeId, channelType: addRecipient.channelType })
  addRecipient.assigneeId = null
  addRecipient.channelType = 'DINGDING'
}

const removeRecipientRow = (i) => {
  recipientRows.value.splice(i, 1)
}

const openCreateModal = async () => {
  modalTitle.value = '新建日报'
  editingId.value = null
  formData.name = ''
  formData.cronExpr = ''
  formData.enabled = false
  formData.templateId = null
  formData.statStartTime = ''
  formData.statEndTime = ''
  formRuleIds.value = []
  formChannelIds.value = []
  recipientRows.value = []
  originalRecipients.value = []
  originalRulesOfEdit.value = []
  originalChannelIdsOfEdit.value = []
  originalTrendRuleIds.value = []
  formTrendRuleIds.value = []
  addRecipient.assigneeId = null
  addRecipient.channelType = 'DINGDING'
  await loadOptions()
  modalVisible.value = true
}

const edit = async (row) => {
  modalTitle.value = '编辑日报'
  editingId.value = row.id
  formData.name = row.name
  formData.cronExpr = row.cronExpr || ''
  formData.enabled = row.enabled === true
  formData.templateId = row.templateId || null
  formData.statStartTime = hhmm(row.statStartTime)
  formData.statEndTime = hhmm(row.statEndTime)
  formRuleIds.value = []
  formChannelIds.value = []
  recipientRows.value = []
  originalRecipients.value = []
  originalTrendRuleIds.value = []
  formTrendRuleIds.value = []
  addRecipient.assigneeId = null
  addRecipient.channelType = 'DINGDING'
  await loadOptions()
  try {
    await fetchSubResources(row.id)
  } catch (e) {
    console.error(e)
  }
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (!formData.name?.trim()) {
    Message.warning({ content: '请填写日报名称' })
    return
  }
  if (!formData.cronExpr?.trim()) {
    Message.warning({ content: '请填写 Cron 表达式' })
    return
  }
  let reportId
  try {
    if (editingId.value) {
      await request.put(`/daily-reports/${editingId.value}`, {
        name: formData.name,
        cronExpr: formData.cronExpr.trim(),
        enabled: formData.enabled === true,
        templateId: formData.templateId,
        statStartTime: formData.statStartTime || null,
        statEndTime: formData.statEndTime || null
      })
      reportId = editingId.value
    } else {
      const res = await request.post('/daily-reports', {
        name: formData.name,
        cronExpr: formData.cronExpr.trim(),
        enabled: formData.enabled === true,
        templateId: formData.templateId,
        statStartTime: formData.statStartTime || null,
        statEndTime: formData.statEndTime || null
      })
      reportId = res.data?.id
    }
    await syncSubResources(reportId)
    modalVisible.value = false
    Message.success({ content: '已保存' })
    fetch()
  } catch (e) {
    console.error(e)
  }
}

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: sub-resources are separate endpoints — diff the edited set against
// the originally loaded one and POST the additions / DELETE the removals (recipients use their row id for delete)
const syncSubResources = async (reportId) => {
  const curRuleIds = formRuleIds.value || []
  const prevRuleIds = (originalRulesOfEdit.value || []).map(x => x.ruleId)
  for (const ruleId of curRuleIds.filter(id => !prevRuleIds.includes(id))) {
    await request.post(`/daily-reports/${reportId}/rules`, { ruleId })
  }
  for (const ruleId of prevRuleIds.filter(id => !curRuleIds.includes(id))) {
    await request.delete(`/daily-reports/${reportId}/rules/${ruleId}`)
  }

  const curRecipients = recipientRows.value || []
  const prevRecipients = originalRecipients.value || []
  const sameRecipient = (a, b) => a.assigneeId === b.assigneeId && a.channelType === b.channelType
  for (const r of curRecipients.filter(r => !prevRecipients.some(o => sameRecipient(o, r)))) {
    await request.post(`/daily-reports/${reportId}/recipients`, { assigneeId: r.assigneeId, channelType: r.channelType })
  }
  for (const o of prevRecipients.filter(o => !curRecipients.some(r => sameRecipient(o, r)))) {
    await request.delete(`/daily-reports/${reportId}/recipients/${o.id}`)
  }

  const curChannelIds = formChannelIds.value || []
  const prevChannelIds = (originalChannelIdsOfEdit.value || []).map(x => x.channelId)
  for (const channelId of curChannelIds.filter(id => !prevChannelIds.includes(id))) {
    await request.post(`/daily-reports/${reportId}/channels`, { channelId })
  }
  for (const channelId of prevChannelIds.filter(id => !curChannelIds.includes(id))) {
    await request.delete(`/daily-reports/${reportId}/channels/${channelId}`)
  }

  // #AI-CODE-FUNCTION-dev_release/v0.0.2-20261010: trend monitoring items (rules) are their own sub-resource
  const curTrendIds = formTrendRuleIds.value || []
  const prevTrendIds = (originalTrendRuleIds.value || []).map(x => x.ruleId)
  for (const ruleId of curTrendIds.filter(id => !prevTrendIds.includes(id))) {
    await request.post(`/daily-reports/${reportId}/trend-rules`, { ruleId })
  }
  for (const ruleId of prevTrendIds.filter(id => !curTrendIds.includes(id))) {
    await request.delete(`/daily-reports/${reportId}/trend-rules/${ruleId}`)
  }
}

const runReasonText = (reason) => {
  const map = {
    'no-rules': '未配置关联规则',
    'no-recipients': '未解析到接收人/渠道',
    'no-template': '未找到可用模板',
    disabled: '日报已停用'
  }
  return map[reason] || reason || '未知原因'
}

const runNow = async (row) => {
  try {
    const res = await processorRequest.post(`/reports/${row.id}/run`)
    const data = res.data || {}
    if (data.sent) {
      Message.success({ content: `日报「${row.name}」已生成并发送（${data.alertCount} 条告警 / ${data.recipientCount} 个接收人）` })
    } else {
      Message.warning({ content: `日报「${row.name}」未发送：${runReasonText(data.reason)}`, duration: 6 })
    }
    fetch()
  } catch (e) {
    Message.error({ content: e.response?.data?.message || '生成失败' })
  }
}

const remove = (id) => {
  Modal.confirm({
    title: '删除确认',
    content: '确定删除该日报吗？其规则/接收人/渠道关联将一并删除。',
    onOk: async () => {
      await request.delete(`/daily-reports/${id}`)
      Message.success({ content: '已删除' })
      fetch()
    }
  })
}

// options are shared by the table (template name) and the create/edit modal, so load them once on mount
onMounted(() => {
  fetch()
  loadOptions()
})
</script>

<style scoped>
.table-card { border-radius: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--aw-text); }
.pager { margin-top: 12px; text-align: right; }
.form-tip { margin-left: 10px; font-size: 12px; color: var(--aw-text-faint); }
.range-sep { margin: 0 8px; color: var(--aw-text-faint); }
.mono-cell { font-family: Consolas, monospace; font-size: 12px; color: var(--aw-text-secondary); }

.recipient-list { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.recipient-add { display: flex; gap: 8px; }
.recipient-rows { display: flex; flex-direction: column; gap: 6px; }
.recipient-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 6px 10px;
}
.member-name { font-size: 13px; color: var(--aw-text); }
.member-empty { text-align: center; color: var(--aw-text-faint); font-size: 13px; padding: 12px 0; }

/* 让穿梭框内的规则名换行显示，避免名称过长被截断 */
.rule-transfer :deep(.ivu-transfer-list-content-item) {
  white-space: normal;
  line-height: 1.5;
  padding-top: 4px;
  padding-bottom: 4px;
}
.rule-transfer :deep(.ivu-transfer-list-content-item .ivu-checkbox-wrapper) {
  display: flex;
  align-items: flex-start;
  white-space: normal;
  word-break: break-word;
}
.rule-transfer :deep(.ivu-transfer-list-content-item .ivu-checkbox) {
  flex-shrink: 0;
  margin-top: 2px;
}
</style>
