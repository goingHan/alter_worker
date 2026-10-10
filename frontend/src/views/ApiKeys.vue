// #AI-CODE-FILE-dev_release/v0.0.1-20260901: unify theme + Chinese UI + add missing apiValue field
<template>
  <div>
    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">API 密钥</span>
        <Button type="primary" size="small" @click="openCreateModal">
          <Icon type="ios-add" /> 新建密钥
        </Button>
      </div>
      <Table :columns="columns" :data="data" :loading="loading">
        <template #action="{ row }">
          <Button size="small" @click="edit(row)">编辑</Button>
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
          <Input v-model="formData.name" placeholder="密钥名称" />
        </FormItem>
        <FormItem v-if="!editingId" label="密钥值">
          <div class="form-tip standalone">由系统自动生成，创建后仅显示一次</div>
        </FormItem>
        <FormItem v-else label="密钥值">
          <Input v-model="formData.apiValue" placeholder="直接修改即为更新密钥" />
          <div class="form-tip">此处显示原始密钥，直接修改即为更新密钥；留空则保持不变</div>
        </FormItem>
        <FormItem label="速率限制">
          <InputNumber v-model="formData.rateLimit" :min="1" :max="10000" style="width: 100%" />
          <div class="form-tip">单位：次/分钟，超出后该密钥将被限流</div>
        </FormItem>
      </Form>
    </Modal>

    <Modal v-model="resultVisible" title="密钥创建成功" :mask-closable="false">
      <Alert type="warning" show-icon>密钥仅显示一次，请立即复制保存。关闭后将只显示掩码。</Alert>
      <div class="key-row">
        <Input v-model="generatedKey" readonly />
        <Button type="primary" @click="copyKey">复制</Button>
      </div>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Modal, Message, Alert } from 'view-ui-plus'
import request from '../api/request.js'
import { fmtTime } from '../utils/time.js'

const loading = ref(false)
const data = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)
const modalVisible = ref(false)
const modalTitle = ref('新建密钥')
const editingId = ref(null)

const formData = reactive({ name: '', apiValue: '', rateLimit: 100 })
const resultVisible = ref(false)
const generatedKey = ref('')

const columns = [
  { title: '名称', key: 'name' },
  { title: '密钥值', key: 'apiValue', minWidth: 180 },
  { title: '速率限制(次/分)', key: 'rateLimit', width: 130, align: 'center' },
  { title: '创建时间', key: 'createdAt', width: 170,
    render: (h, p) => h('span', {}, fmtTime(p.row.createdAt)) },
  { title: '操作', slot: 'action', width: 140 }
]

const fetch = async () => {
  loading.value = true
  try {
    const res = await request.get('/api-keys', { params: { current: page.value, size: pageSize.value } })
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

const remove = async (id) => {
  Modal.confirm({
    title: '删除确认',
    content: '确定删除该 API 密钥吗？删除后使用该密钥的告警将被拒绝。',
    onOk: async () => {
      await request.delete(`/api-keys/${id}`)
      Message.success({ content: '已删除' })
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = '新建密钥'
  editingId.value = null
  formData.name = ''
  formData.apiValue = ''
  formData.rateLimit = 100
  modalVisible.value = true
}

const edit = async (row) => {
  modalTitle.value = '编辑密钥'
  editingId.value = row.id
  formData.name = row.name
  formData.apiValue = row.apiValue || ''
  formData.rateLimit = row.rateLimit || 100
  try {
    const res = await request.get(`/api-keys/${row.id}/reveal`)
    formData.apiValue = res.data?.apiValue || ''
  } catch {
    // 获取原始密钥失败时回退到列表中的掩码值
  }
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (!formData.name?.trim()) {
    Message.warning({ content: '请填写密钥名称' })
    return
  }
  if (editingId.value) {
    await request.put(`/api-keys/${editingId.value}`, formData)
    modalVisible.value = false
    Message.success({ content: '已保存' })
  } else {
    const res = await request.post('/api-keys', { name: formData.name, rateLimit: formData.rateLimit })
    generatedKey.value = res.data?.apiValue || ''
    modalVisible.value = false
    resultVisible.value = true
    Message.success({ content: '已创建' })
  }
  fetch()
}

const copyKey = async () => {
  const text = generatedKey.value
  if (!text) {
    Message.warning({ content: '没有可复制的内容' })
    return
  }
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      Message.success({ content: '已复制' })
      return
    } catch {
      // fall through to legacy copy
    }
  }
  if (fallbackCopy(text)) {
    Message.success({ content: '已复制' })
  } else {
    Message.warning({ content: '复制失败，请手动选择复制' })
  }
}

const fallbackCopy = (text) => {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()
  textarea.setSelectionRange(0, text.length)
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }
  document.body.removeChild(textarea)
  return ok
}

onMounted(fetch)
</script>

<style scoped>
.table-card { border-radius: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--aw-text); }
.pager { margin-top: 12px; text-align: right; }
.form-tip { margin-left: 10px; font-size: 12px; color: var(--aw-text-faint); }
.form-tip.standalone { margin-left: 0; }
.key-row { display: flex; gap: 8px; margin-top: 12px; }
</style>
