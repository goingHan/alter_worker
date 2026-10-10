// #AI-CODE-FILE-dev_release/v0.0.1-20260901: unify theme + Chinese UI + pagination + JSON format
<template>
  <div>
    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">通知渠道</span>
        <Button type="primary" size="small" @click="openCreateModal">
          <Icon type="ios-add" /> 新建渠道
        </Button>
      </div>
      <Table :columns="columns" :data="data" :loading="loading">
        <template #action="{ row }">
          <Button size="small" @click="testChannel(row.id)">测试</Button>
          <Button size="small" @click="edit(row)">编辑</Button>
          <Button size="small" :type="row.isDefault ? 'default' : 'primary'" @click="setDefault(row, !row.isDefault)">
            {{ row.isDefault ? '取消默认' : '设为默认' }}
          </Button>
          <Button size="small" type="error" @click="remove(row.id)">删除</Button>
        </template>
      </Table>
      <div class="pager">
        <Page :total="total" :page-size="pageSize" :current="page" show-sizer :page-size-opts="[10, 15, 20, 50, 100]" @on-change="onPageChange" @on-page-size-change="onPageSizeChange" />
      </div>
    </Card>

    <Modal v-model="modalVisible" :title="modalTitle" @on-ok="handleSubmit">
      <Form :model="formData" :label-width="90">
        <FormItem label="名称">
          <Input v-model="formData.name" placeholder="渠道名称" />
        </FormItem>
        <FormItem label="渠道类型">
          <Select v-model="formData.channelType" filterable placeholder="选择渠道类型">
            <Option value="DINGDING">钉钉</Option>
            <Option value="EMAIL">邮件</Option>
          </Select>
        </FormItem>
        <FormItem label="默认模板">
          <Select v-model="formData.defaultTemplateId" clearable filterable placeholder="选择默认模板（可选）">
            <Option v-for="t in allTemplates" :key="t.id" :value="t.id">{{ t.name }}</Option>
          </Select>
        </FormItem>
        <FormItem label="默认通道">
          <Checkbox v-model="formData.isDefault">设为该渠道类型的默认通道（同类型仅一个）</Checkbox>
          <div class="form-tip">未显式绑定通道的规则会走默认通道</div>
        </FormItem>
        <FormItem label="配置 JSON">
          <Input v-model="formData.configJson" type="textarea" :rows="6" :placeholder="configJsonPlaceholder" />
          <div class="json-actions">
            <Button size="small" @click="formatConfigJson">格式化 JSON</Button>
          </div>
          <div class="config-hint">
            <div class="hint-block">
              <span class="hint-title">钉钉 · DINGDING</span>
              <code class="hint-code">{ "webhookUrl": "…", "secret": "可选", "msgType": "text | markdown | action_card", "keyword": ["告警"] }</code>
            </div>
            <div class="hint-block">
              <span class="hint-title">邮件 · EMAIL</span>
              <code class="hint-code">{ "from": "…", "subject": "…", "format": "html | text" }</code>
            </div>
          </div>
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Modal, Message } from 'view-ui-plus'
import request, { gatewayRequest } from '../api/request.js'
import { prettyJson } from '../utils/json'
import { fmtTime } from '../utils/time.js'

const loading = ref(false)
const data = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)
const modalVisible = ref(false)
const modalTitle = ref('新建渠道')
const editingId = ref(null)

const configJsonPlaceholder = '{ "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxx", "secret": "SECxxx", "msgType": "text", "keyword": ["告警"] }'

const formData = reactive({ name: '', channelType: '', configJson: '', defaultTemplateId: null, isDefault: false })
const allTemplates = ref([])

const channelTypeText = (type) => (type === 'EMAIL' ? '邮件' : type === 'DINGDING' ? '钉钉' : (type || '--'))

const columns = [
  { title: '名称', key: 'name' },
  { title: '渠道类型', key: 'channelType', width: 110, render: (h, p) => h('span', {}, channelTypeText(p.row.channelType)) },
  {
    title: '默认',
    key: 'isDefault',
    width: 80,
    align: 'center',
    render: (h, p) => (p.row.isDefault ? h('Tag', { props: { color: 'green' } }, () => '默认') : h('span', {}, '—'))
  },
  { title: '创建时间', key: 'createdAt', width: 170,
    render: (h, p) => h('span', {}, fmtTime(p.row.createdAt)) },
  { title: '操作', slot: 'action', width: 270 }
]

const fetchTemplates = async () => {
  try {
    const res = await request.get('/templates', { params: { current: 1, size: 1000 } })
    allTemplates.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  }
}

const fetch = async () => {
  loading.value = true
  try {
    const res = await request.get('/channels', { params: { current: page.value, size: pageSize.value } })
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

const testChannel = async (id) => {
  try {
    await gatewayRequest.post(`/channels/${id}/test`)
    Message.success({ content: '测试消息已发送' })
  } catch (e) {
    Message.error({ content: '测试消息发送失败' })
  }
}

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: per-type default channel used by rules without explicit bindings
const setDefault = async (row, value) => {
  try {
    await request.put(`/channels/${row.id}/default`, { default: value })
    Message.success({ content: value ? '已设为默认通道' : '已取消默认' })
    fetch()
  } catch (e) {
    console.error(e)
  }
}

const formatConfigJson = () => {
  const formatted = prettyJson(formData.configJson)
  if (formatted === formData.configJson && formData.configJson.trim()) {
    Message.warning({ content: 'JSON 格式不正确，无法格式化' })
  }
  formData.configJson = formatted
}

const remove = async (id) => {
  Modal.confirm({
    title: '删除确认',
    content: '确定删除该通知渠道吗？此操作不可撤销。',
    onOk: async () => {
      await request.delete(`/channels/${id}`)
      Message.success({ content: '已删除' })
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = '新建渠道'
  editingId.value = null
  formData.name = ''
  formData.channelType = ''
  formData.configJson = ''
  formData.defaultTemplateId = null
  formData.isDefault = false
  fetchTemplates()
  modalVisible.value = true
}

const edit = (row) => {
  modalTitle.value = '编辑渠道'
  editingId.value = row.id
  formData.name = row.name
  formData.channelType = row.channelType
  formData.configJson = row.configJson || ''
  formData.defaultTemplateId = row.defaultTemplateId || null
  formData.isDefault = !!row.isDefault
  fetchTemplates()
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (editingId.value) {
    await request.put(`/channels/${editingId.value}`, formData)
  } else {
    await request.post('/channels', formData)
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
.pager { margin-top: 12px; text-align: right; }

.json-actions { margin: 8px 0; }
.form-tip { font-size: 12px; color: var(--aw-text-faint); margin-top: 6px; }
.config-hint {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 10px 12px;
}
.hint-block { display: flex; flex-direction: column; gap: 2px; }
.hint-title { font-size: 12px; font-weight: 600; color: var(--aw-text-secondary); }
.hint-code {
  font-family: Consolas, monospace;
  font-size: 12px;
  color: var(--aw-text-faint);
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
