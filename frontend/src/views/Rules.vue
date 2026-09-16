// #AI-CODE-FILE-dev_release/v0.0.1-20260902: transfer-based batch group/assignee association + template link + edit cancel
<template>
  <div>
    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">告警规则</span>
        <div class="header-actions">
          <Button size="small" :disabled="!selectedIds.length" @click="openBatchManage">批量管理群组/人员</Button>
          <Button size="small" :disabled="!selectedIds.length" @click="openBatchAssociate">批量关联模板</Button>
          <Button size="small" :disabled="!selectedIds.length" @click="openBatchChannels">批量关联渠道</Button>
          <Button size="small" :disabled="!selectedIds.length" @click="openTestModal">测试告警</Button>
          <Button size="small" type="error" :disabled="!selectedIds.length" @click="batchDelete">批量删除</Button>
          <Button type="primary" size="small" @click="openCreateModal">
            <Icon type="ios-add" /> 新建规则
          </Button>
        </div>
      </div>

      <div class="filter-bar">
        <Input v-model="filters.name" placeholder="规则名称" clearable class="filter-item" @on-enter="search" />
        <Select v-model="filters.alertType" clearable filterable placeholder="告警类型" class="filter-item">
          <Option value="PROMETHEUS">Prometheus</Option>
          <Option value="JSON">JSON</Option>
        </Select>
        <Button type="primary" size="small" @click="search">查询</Button>
        <Button size="small" @click="reset">重置</Button>
      </div>

      <Table :columns="columns" :data="data" :loading="loading" @on-selection-change="onSelectionChange">
        <template #action="{ row }">
          <Button size="small" @click="openLink(row)">关联模板</Button>
          <Button size="small" @click="edit(row)">编辑</Button>
          <Button size="small" type="error" @click="remove(row.id)">删除</Button>
        </template>
      </Table>
      <div class="pager">
        <Page :total="total" :page-size="pageSize" :current="page" show-sizer :page-size-opts="[10, 15, 20, 50, 100]" @on-change="onPageChange" @on-page-size-change="onPageSizeChange" />
      </div>
    </Card>

    <Modal v-model="modalVisible" :title="modalTitle" width="760" @on-ok="handleSubmit">
      <Form :model="formData" :label-width="90">
        <FormItem label="名称">
          <Input v-model="formData.name" placeholder="规则名称" />
        </FormItem>
        <FormItem label="告警类型">
          <Select v-model="formData.alertType" filterable placeholder="选择告警类型">
            <Option value="PROMETHEUS">Prometheus</Option>
            <Option value="JSON">JSON</Option>
          </Select>
        </FormItem>
        <FormItem label="匹配条件">
          <div class="match-list">
            <div v-for="(m, i) in matchList" :key="i" class="match-row">
              <Select v-model="m.op" :disabled="i === 0" class="match-op" transfer>
                <Option value="AND">且</Option>
                <Option value="OR">或</Option>
                <Option value="NOT">非</Option>
              </Select>
              <Input v-model="m.key" placeholder="匹配 Key（label 名 / key 名）" class="match-key" />
              <Input v-model="m.value" placeholder="匹配值（留空=字段存在即命中）" class="match-value" />
              <Button size="small" type="error" :disabled="matchList.length === 1" @click="removeMatch(i)">−</Button>
            </div>
            <Button size="small" @click="addMatch">+ 添加条件</Button>
            <div class="form-tip">运算符自上而下顺序计算；「非」表示该条件不成立（字段不存在时也算命中）</div>
          </div>
        </FormItem>
        <FormItem label="最大重试">
          <InputNumber v-model="formData.retryMax" :min="1" :max="10" style="width: 100%" />
        </FormItem>
        <FormItem label="失败重试退避">
          <Input v-model="formData.retryBackoff" placeholder="如 5s,10s,20s" />
        </FormItem>
        <FormItem label="关联渠道">
          <Select v-model="formChannelIds" multiple filterable placeholder="默认：走渠道类型默认通道">
            <Option v-for="c in allChannels" :key="c.id" :value="c.id">{{ c.name }}（{{ channelTypeText(c.channelType) }}）</Option>
          </Select>
          <div class="form-tip">可多选，同类型多条会全部发送；留空时走各渠道类型的默认通道</div>
        </FormItem>
        <FormItem v-if="editingId" label="已关联群组">
          <div class="assoc-tags">
            <Tag v-for="g in editGroups" :key="g.groupId" closable @on-close="cancelEditGroup(g.groupId)">{{ g.groupName }}</Tag>
            <span v-if="!editGroups.length" class="assoc-empty">无</span>
          </div>
        </FormItem>
        <FormItem v-if="editingId" label="已关联人员">
          <div class="assoc-tags">
            <Tag v-for="a in editAssignees" :key="a.assigneeId" closable @on-close="cancelEditAssignee(a.assigneeId)">{{ a.cnName }}（{{ channelTypeText(a.channelType) }}）</Tag>
            <span v-if="!editAssignees.length" class="assoc-empty">无</span>
          </div>
        </FormItem>
      </Form>
    </Modal>

    <Modal v-model="linkVisible" title="关联消息模板" width="520" :footer-hide="true">
      <div class="link-list">
        <div class="link-row">
          <span class="link-name">钉钉模板</span>
          <Select v-model="dingTemplateId" clearable filterable style="width: 280px" placeholder="选择钉钉模板" @on-change="setTemplate('DINGDING', $event)">
            <Option v-for="t in dingTemplates" :key="t.id" :value="t.id">{{ t.name }}</Option>
          </Select>
        </div>
        <div class="link-row">
          <span class="link-name">邮件模板</span>
          <Select v-model="emailTemplateId" clearable filterable style="width: 280px" placeholder="选择邮件模板" @on-change="setTemplate('EMAIL', $event)">
            <Option v-for="t in emailTemplates" :key="t.id" :value="t.id">{{ t.name }}</Option>
          </Select>
        </div>
        <div class="form-tip">未配置时回落到默认模板；渠道绑定请在编辑规则或「批量关联渠道」中设置</div>
      </div>
    </Modal>

    <Modal v-model="batchChannelsVisible" title="批量关联渠道" @on-ok="applyBatchChannels">
      <Form :label-width="90">
        <FormItem label="渠道">
          <Select v-model="batchChannelIds" multiple filterable style="width: 100%" placeholder="留空=解除全部渠道绑定">
            <Option v-for="c in allChannels" :key="c.id" :value="c.id">{{ c.name }}（{{ channelTypeText(c.channelType) }}）</Option>
          </Select>
        </FormItem>
      </Form>
      <div class="form-tip">将选中 {{ selectedIds.length }} 条规则的渠道统一替换为所选渠道；留空 = 解除全部绑定，回落到默认通道</div>
    </Modal>

    <Modal v-model="batchVisible" title="批量关联模板" @on-ok="batchAssociate">
      <Form :label-width="90">
        <FormItem label="模板">
          <Select v-model="batchTemplateId" filterable style="width: 100%" placeholder="选择模板">
            <Option v-for="t in allTemplates" :key="t.id" :value="t.id">{{ t.name }}</Option>
          </Select>
        </FormItem>
        <FormItem label="渠道类型">
          <Select v-model="batchChannelType" filterable style="width: 100%">
            <Option value="DINGDING">钉钉</Option>
            <Option value="EMAIL">邮件</Option>
          </Select>
        </FormItem>
      </Form>
      <div class="form-tip">将应用到选中的 {{ selectedIds.length }} 条规则</div>
    </Modal>

    <Modal v-model="batchManageVisible" title="批量管理群组和值班人员" width="720" @on-ok="batchManageApply">
      <Form :label-width="90">
        <FormItem label="群组">
          <Transfer :data="groupTransferData" :target-keys="groupTargetKeys" filterable :titles="['未关联群组', '已关联群组']" @on-change="onGroupTransferChange" />
        </FormItem>
        <FormItem label="值班人员">
          <Transfer :data="assigneeTransferData" :target-keys="assigneeTargetKeys" filterable :titles="['未关联人员', '已关联人员']" @on-change="onAssigneeTransferChange" />
        </FormItem>
        <FormItem label="人员渠道">
          <Select v-model="batchAssigneeChannelType" filterable style="width: 180px">
            <Option value="DINGDING">钉钉</Option>
            <Option value="EMAIL">邮件</Option>
          </Select>
          <div class="form-tip">仅用于新增关联；移回左侧会删除该人员与规则的全部渠道关联</div>
        </FormItem>
      </Form>
      <div class="form-tip">应用到选中的 {{ selectedIds.length }} 条规则：移入右侧=建立关联，移回左侧=取消关联</div>
    </Modal>

    <Modal v-model="testVisible" title="测试告警" width="760">
      <div class="test-body">
        <div class="test-line">
          <span class="link-name">选中规则</span>
          <div class="assoc-tags">
            <Tag v-for="r in selectedRows" :key="r.id">{{ r.name }}（{{ alertTypeText(r.alertType) }}）</Tag>
            <span v-if="!selectedRows.length" class="assoc-empty">无</span>
          </div>
        </div>
        <div class="test-line">
          <span class="link-name">告警通道</span>
          <Select v-model="testChannelId" clearable filterable style="width: 340px" placeholder="默认：按规则绑定/默认通道">
            <Option v-for="c in allChannels" :key="c.id" :value="c.id">{{ c.name }}（{{ channelTypeText(c.channelType) }}）</Option>
          </Select>
          <span class="test-hint">留空=按规则绑定的渠道发送（未配置则走默认通道）；选中=强制走该通道</span>
        </div>
        <div class="test-line">
          <span class="link-name">数据来源</span>
          <RadioGroup v-model="testMode">
            <Radio label="auto">随机生成（跳过匹配，直接用选中规则）</Radio>
            <Radio label="payload">手动输入报文（走真实规则匹配）</Radio>
          </RadioGroup>
        </div>
        <div v-if="testMode === 'payload'" class="test-line">
          <span class="link-name">报文类型</span>
          <Select v-model="testAlertType" style="width: 160px">
            <Option value="PROMETHEUS">Prometheus</Option>
            <Option value="JSON">JSON</Option>
          </Select>
        </div>
        <div v-if="testMode === 'payload'" class="test-payload">
          <Input v-model="testRawPayload" type="textarea" :rows="9" placeholder='粘贴告警报文，例如 {"alertname":"CPUHigh","service_type":"core","title":"测试","content":"hello"}' />
        </div>
        <Table v-if="testResults.length" :columns="testResultColumns" :data="testResults" size="small" />
        <div class="form-tip">测试消息会进入正常链路（队列→worker 发送），最终状态请在「告警历史」查看；失败详情见「失败消息」</div>
      </div>
      <template #footer>
        <Button @click="testVisible = false">取消</Button>
        <Button type="primary" :loading="testLoading" @click="submitTest">发送测试</Button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Modal, Message } from 'view-ui-plus'
import request, { gatewayRequest } from '../api/request.js'

const loading = ref(false)
const data = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)
const modalVisible = ref(false)
const modalTitle = ref('新建规则')
const editingId = ref(null)

const selectedIds = ref([])
const selectedRows = ref([])
const groupNamesMap = ref({})
const channelNamesMap = ref({})
const filters = reactive({ name: '', alertType: '' })

const linkVisible = ref(false)
const linkRuleId = ref(null)
const allTemplates = ref([])
const serverTemplates = ref({})
const dingTemplateId = ref(null)
const emailTemplateId = ref(null)

const allChannels = ref([])

const batchChannelsVisible = ref(false)
const batchChannelIds = ref([])

const testVisible = ref(false)
const testChannelId = ref(null)
const testMode = ref('auto')
const testAlertType = ref('PROMETHEUS')
const testRawPayload = ref('')
const testResults = ref([])
const testLoading = ref(false)

const batchVisible = ref(false)
const batchTemplateId = ref(null)
const batchChannelType = ref('DINGDING')
const batchAssigneeChannelType = ref('DINGDING')

const batchManageVisible = ref(false)
const allGroups = ref([])
const allAssignees = ref([])
const groupTransferData = ref([])
const assigneeTransferData = ref([])
const groupTargetKeys = ref([])
const assigneeTargetKeys = ref([])

const editGroups = ref([])
const editAssignees = ref([])

const formData = reactive({ name: '', alertType: '', retryMax: 3, retryBackoff: '5s,10s,20s' })
const matchList = ref([{ key: '', value: '', op: 'AND' }])
const formChannelIds = ref([])

const alertTypeText = (t) => (t === 'JSON' ? 'JSON' : t === 'PROMETHEUS' ? 'Prometheus' : (t || '--'))
const channelTypeText = (t) => (t === 'EMAIL' ? '邮件' : t === 'DINGDING' ? '钉钉' : (t || '--'))

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: 每条条件的有效运算符；新数据读 op，旧数据用 not/matchLogic 兼容
const conditionOp = (c, i, legacyLogic) => {
  const op = (c.op || '').toUpperCase()
  if (op === 'AND' || op === 'OR' || op === 'NOT') return op
  if (c.not) return 'NOT'
  return i === 0 ? 'AND' : legacyLogic
}

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: 汇总展示匹配条件（每条条件带 且/或/非，自上而下左结合；NOT 用 ≠ 表示）
const matchSummary = (row) => {
  let conds = null
  try {
    const arr = row.matchConditions ? JSON.parse(row.matchConditions) : null
    if (Array.isArray(arr) && arr.length) conds = arr
  } catch (e) {
    // ignore
  }
  if (!conds) conds = [{ key: row.matchKey, value: row.matchValue }]
  const legacyLogic = (row.matchLogic || 'OR').toUpperCase() === 'AND' ? 'AND' : 'OR'
  return conds
    .map((c, i) => {
      const v = c.value == null || c.value === '' ? '(存在)' : c.value
      const op = conditionOp(c, i, legacyLogic)
      const expr = `${c.key}${op === 'NOT' ? '≠' : '='}${v}`
      return i === 0 ? expr : `${op === 'OR' ? ' OR ' : ' AND '}${expr}`
    })
    .join('')
}

const columns = [
  { type: 'selection', width: 50, align: 'center' },
  { title: '名称', key: 'name' },
  { title: '告警类型', key: 'alertType', width: 110, render: (h, p) => h('span', {}, alertTypeText(p.row.alertType)) },
  { title: '匹配条件', minWidth: 220, render: (h, p) => h('span', { class: 'match-summary' }, matchSummary(p.row)) },
  { title: '关联群组', key: 'groups', render: (h, p) => {
    const names = groupNamesMap.value[p.row.id] || []
    return h('span', {}, names.length ? names.join('、') : '—')
  } },
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: rule-bound channels (multiple allowed); empty = falls back to default channels
  { title: '关联渠道', key: 'channels', width: 170, render: (h, p) => {
    const names = channelNamesMap.value[p.row.id] || []
    return names.length
      ? h('span', { class: 'match-summary' }, names.join('、'))
      : h('span', { class: 'assoc-empty' }, '默认通道')
  } },
  { title: '重试', key: 'retryMax', width: 70, align: 'center' },
  { title: '操作', slot: 'action', width: 210 }
]

const testResultColumns = [
  { title: '规则', key: 'ruleName', render: (h, p) => h('span', {}, p.row.ruleName || (p.row.matchedRuleName ? `（匹配）${p.row.matchedRuleName}` : '—')) },
  { title: '报文号', key: 'messageNo', width: 170 },
  { title: '接收人数', key: 'recipientCount', width: 90, align: 'center' },
  {
    title: '结果',
    key: 'success',
    width: 90,
    render: (h, p) => h('Tag', { props: { color: p.row.success ? 'green' : 'red' } }, () => (p.row.success ? '已提交' : '失败'))
  },
  { title: '错误', key: 'error', render: (h, p) => h('span', {}, p.row.error || '—') }
]

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: templates are channel-agnostic now, the channel is chosen by which
// select you bind to, so both dropdowns list every template instead of filtering by template.channelType
const dingTemplates = computed(() => allTemplates.value)
const emailTemplates = computed(() => allTemplates.value)

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: channel list shared by the rule form, batch modal and test modal
const loadChannels = async () => {
  if (allChannels.value.length) {
    return
  }
  try {
    const res = await request.get('/channels', { params: { current: 1, size: 1000 } })
    allChannels.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  }
}

const groupName = (id) => {
  const g = allGroups.value.find(x => x.id === id)
  return g ? g.name : String(id)
}
const assigneeName = (id) => {
  const a = allAssignees.value.find(x => x.id === id)
  return a ? a.cnName : String(id)
}

const fetch = async () => {
  loading.value = true
  try {
    const res = await request.get('/rules', {
      params: {
        current: page.value,
        size: pageSize.value,
        name: filters.name || undefined,
        alertType: filters.alertType || undefined
      }
    })
    data.value = res.data?.records || []
    total.value = res.data?.total || 0
    fetchGroupNames()
    fetchChannelNames()
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  fetch()
}

const reset = () => {
  filters.name = ''
  filters.alertType = ''
  search()
}

const fetchGroupNames = async () => {
  try {
    const res = await request.get('/rules/groups')
    const map = {}
    ;(res.data || []).forEach(x => {
      if (!map[x.ruleId]) map[x.ruleId] = []
      map[x.ruleId].push(x.groupName || x.groupId)
    })
    groupNamesMap.value = map
  } catch (e) {
    console.error(e)
  }
}

const fetchChannelNames = async () => {
  try {
    const res = await request.get('/rules/channels')
    const map = {}
    ;(res.data || []).forEach(x => {
      if (!map[x.ruleId]) map[x.ruleId] = []
      map[x.ruleId].push(x.channelName || x.channelId)
    })
    channelNamesMap.value = map
  } catch (e) {
    console.error(e)
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

const onSelectionChange = (selection) => {
  selectedRows.value = selection
  selectedIds.value = selection.map(r => r.id)
}

const addMatch = () => {
  matchList.value.push({ key: '', value: '', op: 'AND' })
}

const removeMatch = (i) => {
  matchList.value.splice(i, 1)
}

const parseConditions = (row) => {
  const legacyLogic = (row.matchLogic || 'OR').toUpperCase() === 'AND' ? 'AND' : 'OR'
  if (row.matchConditions) {
    try {
      const arr = JSON.parse(row.matchConditions)
      if (Array.isArray(arr) && arr.length) {
        return arr.map((c, i) => ({
          key: c.key || '',
          value: c.value || '',
          op: conditionOp(c, i, legacyLogic)
        }))
      }
    } catch (e) {
      // ignore
    }
  }
  return [{ key: row.matchKey || '', value: row.matchValue || '', op: 'AND' }]
}

const openLink = async (row) => {
  linkRuleId.value = row.id
  linkVisible.value = true
  try {
    const [templatesRes, templateLinksRes] = await Promise.all([
      request.get('/templates', { params: { current: 1, size: 1000 } }),
      request.get(`/rules/${row.id}/templates`)
    ])
    allTemplates.value = templatesRes.data?.records || []

    const links = templateLinksRes.data || []
    const map = {}
    links.forEach(l => { map[l.channelType] = l.templateId })
    serverTemplates.value = map
    dingTemplateId.value = map.DINGDING ?? null
    emailTemplateId.value = map.EMAIL ?? null
  } catch (e) {
    console.error(e)
  }
}

const setTemplate = async (channelType, value) => {
  const templateId = value || null
  const old = serverTemplates.value[channelType] || null
  if (old === templateId) return
  try {
    if (old) {
      await request.delete(`/rules/${linkRuleId.value}/templates/${old}`)
    }
    if (templateId) {
      await request.post(`/rules/${linkRuleId.value}/templates`, { templateId, channelType })
      serverTemplates.value = { ...serverTemplates.value, [channelType]: templateId }
    } else {
      const next = { ...serverTemplates.value }
      delete next[channelType]
      serverTemplates.value = next
    }
  } catch (e) {
    console.error(e)
    if (channelType === 'DINGDING') dingTemplateId.value = old
    else emailTemplateId.value = old
  }
}

const remove = async (id) => {
  Modal.confirm({
    title: '删除确认',
    content: '确定删除该规则吗？其组/模板关联将一并删除。',
    onOk: async () => {
      await request.delete(`/rules/${id}`)
      Message.success({ content: '已删除' })
      fetch()
    }
  })
}

const batchDelete = () => {
  if (!selectedIds.value.length) {
    Message.warning({ content: '请先选择规则' })
    return
  }
  Modal.confirm({
    title: '批量删除确认',
    content: `确定删除选中的 ${selectedIds.value.length} 条规则吗？`,
    onOk: async () => {
      await Promise.all(selectedIds.value.map(id => request.delete(`/rules/${id}`)))
      Message.success({ content: '已删除' })
      selectedIds.value = []
      fetch()
    }
  })
}

const openBatchAssociate = async () => {
  batchTemplateId.value = null
  batchChannelType.value = 'DINGDING'
  try {
    const res = await request.get('/templates', { params: { current: 1, size: 1000 } })
    allTemplates.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  }
  batchVisible.value = true
}

const batchAssociate = async () => {
  if (!selectedIds.value.length) {
    Message.warning({ content: '请先选择规则' })
    return
  }
  if (!batchTemplateId.value) {
    Message.warning({ content: '请选择模板' })
    return
  }
  let ok = 0
  for (const id of selectedIds.value) {
    try {
      const existing = await request.get(`/rules/${id}/templates`)
      const cur = (existing.data || []).find(t => t.channelType === batchChannelType.value)
      if (cur) {
        await request.delete(`/rules/${id}/templates/${cur.templateId}`)
      }
      await request.post(`/rules/${id}/templates`, {
        templateId: batchTemplateId.value,
        channelType: batchChannelType.value
      })
      ok++
    } catch (e) {
      console.error(e)
    }
  }
  Message.success({ content: `已关联 ${ok} 条规则` })
  batchVisible.value = false
  selectedIds.value = []
}

const openBatchManage = async () => {
  if (!selectedIds.value.length) {
    Message.warning({ content: '请先选择规则' })
    return
  }
  batchAssigneeChannelType.value = 'DINGDING'
  try {
    const [groupsRes, assigneesRes] = await Promise.all([
      request.get('/groups', { params: { current: 1, size: 1000 } }),
      request.get('/assignees', { params: { current: 1, size: 1000 } })
    ])
    allGroups.value = groupsRes.data?.records || []
    allAssignees.value = assigneesRes.data?.records || []
    groupTransferData.value = allGroups.value.map(g => ({ key: g.id, label: g.name }))
    assigneeTransferData.value = allAssignees.value.map(a => ({ key: a.id, label: a.cnName }))

    const perRule = await Promise.all(selectedIds.value.map(async (id) => {
      const [gRes, aRes] = await Promise.all([
        request.get(`/rules/${id}/groups`),
        request.get(`/rules/${id}/assignees`)
      ])
      return {
        groupIds: (gRes.data || []).map(x => x.groupId),
        assigneeIds: (aRes.data || []).map(x => x.assigneeId)
      }
    }))

    let groupIntersect = null
    let assigneeIntersect = null
    for (const r of perRule) {
      groupIntersect = groupIntersect === null ? [...r.groupIds] : groupIntersect.filter(x => r.groupIds.includes(x))
      assigneeIntersect = assigneeIntersect === null ? [...r.assigneeIds] : assigneeIntersect.filter(x => r.assigneeIds.includes(x))
    }
    groupTargetKeys.value = groupIntersect || []
    assigneeTargetKeys.value = assigneeIntersect || []
    batchManageVisible.value = true
  } catch (e) {
    console.error(e)
  }
}

const onGroupTransferChange = (keys) => {
  groupTargetKeys.value = keys
}

const onAssigneeTransferChange = (keys) => {
  assigneeTargetKeys.value = keys
}

const batchManageApply = async () => {
  if (!selectedIds.value.length) {
    Message.warning({ content: '请先选择规则' })
    return
  }
  let ok = 0
  for (const ruleId of selectedIds.value) {
    try {
      const [gRes, aRes] = await Promise.all([
        request.get(`/rules/${ruleId}/groups`),
        request.get(`/rules/${ruleId}/assignees`)
      ])
      const curGroupIds = (gRes.data || []).map(x => x.groupId)
      const curAssigneeIds = (aRes.data || []).map(x => x.assigneeId)

      const addGroups = groupTargetKeys.value.filter(id => !curGroupIds.includes(id))
      const delGroups = curGroupIds.filter(id => !groupTargetKeys.value.includes(id))
      for (const gid of addGroups) {
        await request.post(`/rules/${ruleId}/groups`, { groupId: gid })
      }
      for (const gid of delGroups) {
        await request.delete(`/rules/${ruleId}/groups/${gid}`)
      }

      const existingAssigneeBindings = aRes.data || []
      const addAssignees = assigneeTargetKeys.value.filter(id =>
        !existingAssigneeBindings.some(x => x.assigneeId === id && x.channelType === batchAssigneeChannelType.value))
      const delAssignees = curAssigneeIds.filter(id => !assigneeTargetKeys.value.includes(id))
      for (const aid of addAssignees) {
        await request.post(`/rules/${ruleId}/assignees`, { assigneeId: aid, channelType: batchAssigneeChannelType.value })
      }
      for (const aid of delAssignees) {
        await request.delete(`/rules/${ruleId}/assignees/${aid}`)
      }
      ok++
    } catch (e) {
      console.error(e)
    }
  }
  Message.success({ content: `已更新 ${ok} 条规则` })
  batchManageVisible.value = false
  selectedIds.value = []
  fetch()
}

const cancelEditGroup = async (groupId) => {
  try {
    await request.delete(`/rules/${editingId.value}/groups/${groupId}`)
    editGroups.value = editGroups.value.filter(g => g.groupId !== groupId)
    fetchGroupNames()
  } catch (e) {
    console.error(e)
  }
}

const cancelEditAssignee = async (assigneeId) => {
  try {
    await request.delete(`/rules/${editingId.value}/assignees/${assigneeId}`)
    editAssignees.value = editAssignees.value.filter(a => a.assigneeId !== assigneeId)
  } catch (e) {
    console.error(e)
  }
}

const openCreateModal = async () => {
  modalTitle.value = '新建规则'
  editingId.value = null
  formData.name = ''
  formData.alertType = ''
  formData.retryMax = 3
  formData.retryBackoff = '5s,10s,20s'
  matchList.value = [{ key: '', value: '', op: 'AND' }]
  formChannelIds.value = []
  editGroups.value = []
  editAssignees.value = []
  await loadChannels()
  modalVisible.value = true
}

const edit = async (row) => {
  modalTitle.value = '编辑规则'
  editingId.value = row.id
  formData.name = row.name
  formData.alertType = row.alertType
  formData.retryMax = row.retryMax || 3
  formData.retryBackoff = row.retryBackoff || '5s,10s,20s'
  matchList.value = parseConditions(row)
  formChannelIds.value = []
  await loadChannels()
  try {
    if (!allGroups.value.length) {
      const gRes = await request.get('/groups', { params: { current: 1, size: 1000 } })
      allGroups.value = gRes.data?.records || []
    }
    if (!allAssignees.value.length) {
      const aRes = await request.get('/assignees', { params: { current: 1, size: 1000 } })
      allAssignees.value = aRes.data?.records || []
    }
    const [gLinks, aLinks, cLinks] = await Promise.all([
      request.get(`/rules/${row.id}/groups`),
      request.get(`/rules/${row.id}/assignees`),
      request.get(`/rules/${row.id}/channels`)
    ])
    editGroups.value = (gLinks.data || []).map(x => ({ groupId: x.groupId, groupName: groupName(x.groupId) }))
    editAssignees.value = (aLinks.data || []).map(x => ({
      assigneeId: x.assigneeId,
      channelType: x.channelType,
      cnName: assigneeName(x.assigneeId)
    }))
    formChannelIds.value = (cLinks.data || []).map(x => x.channelId)
  } catch (e) {
    console.error(e)
    editGroups.value = []
    editAssignees.value = []
    formChannelIds.value = []
  }
  modalVisible.value = true
}

const handleSubmit = async () => {
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: 每条条件保存 {key,value,op}，第一条固定为表达式起点
  const conditions = matchList.value
    .map((m, i) => ({
      key: (m.key || '').trim(),
      value: (m.value || '').trim(),
      op: conditionOp(m, i, 'OR')
    }))
    .filter(c => c.key)
  if (!conditions.length) {
    Message.warning({ content: '请至少填写一个匹配 Key' })
    return
  }
  const payload = {
    name: formData.name,
    alertType: formData.alertType,
    matchKey: conditions[0].key,
    matchValue: conditions[0].value,
    // 始终落 matchConditions：后端 updateById 默认忽略 null 字段，置 null 会保留旧条件导致删除不生效
    matchConditions: JSON.stringify(conditions),
    // 旧字段兼容：含 且/非 视为 AND，否则 OR（新 matcher 以 matchConditions 中的 op 为准）
    matchLogic: conditions.some(c => c.op === 'AND' || c.op === 'NOT') ? 'AND' : 'OR',
    // 全量替换渠道绑定；空数组 = 解除全部绑定（走默认通道）
    channelIds: formChannelIds.value,
    retryMax: formData.retryMax,
    retryBackoff: formData.retryBackoff
  }
  if (editingId.value) {
    await request.put(`/rules/${editingId.value}`, payload)
  } else {
    await request.post('/rules', payload)
  }
  modalVisible.value = false
  Message.success({ content: '已保存' })
  fetch()
}

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: batch channel binding — replace the selected rules' bindings with
// the chosen set (empty = unbind all -> default channels); the modal prefills the intersection of current bindings
const openBatchChannels = async () => {
  if (!selectedIds.value.length) {
    Message.warning({ content: '请先选择规则' })
    return
  }
  batchChannelIds.value = []
  await loadChannels()
  try {
    const perRule = await Promise.all(selectedIds.value.map(async (id) => {
      const res = await request.get(`/rules/${id}/channels`)
      return (res.data || []).map(x => x.channelId)
    }))
    let intersect = null
    for (const ids of perRule) {
      intersect = intersect === null ? [...ids] : intersect.filter(x => ids.includes(x))
    }
    batchChannelIds.value = intersect || []
  } catch (e) {
    console.error(e)
  }
  batchChannelsVisible.value = true
}

const applyBatchChannels = async () => {
  if (!selectedIds.value.length) {
    Message.warning({ content: '请先选择规则' })
    return
  }
  try {
    const res = await request.put('/rules/channels', {
      ruleIds: selectedIds.value,
      channelIds: batchChannelIds.value
    })
    Message.success({ content: `已更新 ${res.data?.updated ?? selectedIds.value.length} 条规则` })
    batchChannelsVisible.value = false
    selectedIds.value = []
    fetch()
  } catch (e) {
    console.error(e)
  }
}

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: test-alert modal — random payloads forced onto the selected rules
// (skip matcher), or a manual payload through the real matcher; both enter the normal MQ pipeline
const openTestModal = async () => {
  if (!selectedIds.value.length) {
    Message.warning({ content: '请先选择规则' })
    return
  }
  testChannelId.value = null
  testMode.value = 'auto'
  testRawPayload.value = ''
  testAlertType.value = selectedRows.value[0]?.alertType || 'PROMETHEUS'
  testResults.value = []
  await loadChannels()
  testVisible.value = true
}

const submitTest = async () => {
  if (!selectedIds.value.length) {
    Message.warning({ content: '请先选择规则' })
    return
  }
  if (testMode.value === 'payload' && !testRawPayload.value.trim()) {
    Message.warning({ content: '请输入报文体，或切换为随机生成' })
    return
  }
  testLoading.value = true
  try {
    const payload = {
      ruleIds: selectedIds.value,
      channelId: testChannelId.value || undefined,
      mode: testMode.value
    }
    if (testMode.value === 'payload') {
      payload.alertType = testAlertType.value
      payload.rawPayload = testRawPayload.value
    }
    const res = await gatewayRequest.post('/test-alerts', payload)
    testResults.value = res.data?.results || []
    Message.success({ content: res.data?.message || '已提交测试告警' })
    fetch()
  } catch (e) {
    console.error(e)
  } finally {
    testLoading.value = false
  }
}

onMounted(fetch)
</script>

<style scoped>
.table-card { border-radius: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--aw-text); }
.header-actions { display: flex; gap: 8px; }
.pager { margin-top: 12px; text-align: right; }
.form-tip { font-size: 12px; color: var(--aw-text-faint); margin-top: 8px; }

.filter-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; align-items: center; }
.filter-item { width: 160px; }

.match-list { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.match-row { display: flex; gap: 8px; align-items: center; flex-wrap: nowrap; }
.match-op { width: 92px; flex: 0 0 92px; }
.match-key { flex: 1 1 0; min-width: 0; }
.match-value { flex: 1 1 0; min-width: 0; }
.match-summary { font-size: 12px; }

.link-list { display: flex; flex-direction: column; gap: 8px; }
.link-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 10px;
  padding: 10px 14px;
}
.link-name { font-size: 13px; color: var(--aw-text); }
.link-empty { text-align: center; color: var(--aw-text-faint); font-size: 13px; padding: 24px 0; }

.assoc-tags { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.assoc-empty { font-size: 12px; color: var(--aw-text-faint); }

.test-body { display: flex; flex-direction: column; gap: 14px; }
.test-line { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.test-hint { font-size: 12px; color: var(--aw-text-faint); }
.test-payload { padding-left: 74px; }
</style>
