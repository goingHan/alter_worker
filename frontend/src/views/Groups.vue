// #AI-CODE-FILE-dev_release/v0.0.1-20260901: unify theme + Chinese UI + fix SendType enum + member binding UI
<template>
  <div>
    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">通信组</span>
        <Button type="primary" size="small" @click="openCreateModal">
          <Icon type="ios-add" /> 新建通信组
        </Button>
      </div>
      <Table :columns="columns" :data="data" :loading="loading">
        <template #action="{ row }">
          <Button size="small" @click="openMembers(row)">成员</Button>
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
          <Input v-model="formData.name" placeholder="通信组名称" />
        </FormItem>
        <FormItem label="发送类型">
          <Select v-model="formData.sendType" filterable placeholder="选择发送类型">
            <Option value="SERVICE_TYPE">按服务类型</Option>
            <Option value="ALL">全部</Option>
          </Select>
          <div class="form-tip">「按服务类型」时，仅向配置了对应 service_type 的人员发送</div>
        </FormItem>
        <FormItem v-if="!editingId" label="值班人员">
          <Select v-model="formData.assigneeIds" multiple filterable placeholder="选择值班人员">
            <Option v-for="a in allAssignees" :key="a.id" :value="a.id">{{ a.cnName }}</Option>
          </Select>
          <div class="form-tip">可选，保存后默认以钉钉方式通知</div>
        </FormItem>
        <FormItem v-if="editingId" label="成员">
          <div class="member-list">
            <div v-for="m in editMembers" :key="m.assigneeId + '-' + m.channelType" class="member-row">
              <span class="member-name">{{ m.cnName }}（{{ channelText(m.channelType) }}）</span>
              <Button size="small" type="error" @click="deleteEditMember(m)">删除</Button>
            </div>
            <div v-if="!editMembers.length" class="member-empty">暂无成员</div>
          </div>
        </FormItem>
      </Form>
    </Modal>

    <Modal v-model="memberVisible" title="成员绑定" width="680" :footer-hide="true">
      <div class="member-add">
        <Select v-model="addForm.assigneeId" filterable placeholder="选择人员" style="width: 160px">
          <Option v-for="a in allAssignees" :key="a.id" :value="a.id">{{ a.cnName }}</Option>
        </Select>
        <Select v-model="addForm.channelType" filterable style="width: 110px">
          <Option value="DINGDING">钉钉</Option>
          <Option value="EMAIL">邮件</Option>
        </Select>
        <Input v-model="addForm.serviceTypes" placeholder="服务类型(逗号分隔)" style="width: 190px" />
        <Button type="primary" size="small" @click="addMember">添加</Button>
      </div>
      <Table :columns="memberColumns" :data="memberList" size="small" class="member-table">
        <template #action="{ row }">
          <Button size="small" type="error" @click="removeMember(row)">移除</Button>
        </template>
      </Table>
      <div v-if="!memberList.length" class="member-empty">暂无成员，请从上方添加</div>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Modal, Message } from 'view-ui-plus'
import request from '../api/request.js'
import { fmtTime } from '../utils/time.js'

const loading = ref(false)
const data = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)
const modalVisible = ref(false)
const modalTitle = ref('新建通信组')
const editingId = ref(null)

const memberVisible = ref(false)
const memberGroupId = ref(null)
const memberList = ref([])
const allAssignees = ref([])
const addForm = reactive({ assigneeId: null, channelType: 'DINGDING', serviceTypes: '' })
const editMembers = ref([])

const formData = reactive({ name: '', sendType: '', assigneeIds: [] })

const sendTypeText = (t) => (t === 'ALL' ? '全部' : t === 'SERVICE_TYPE' ? '按服务类型' : (t || '--'))
const channelText = (t) => (t === 'EMAIL' ? '邮件' : '钉钉')

const columns = [
  { title: '名称', key: 'name' },
  { title: '发送类型', key: 'sendType', width: 130, render: (h, p) => h('span', {}, sendTypeText(p.row.sendType)) },
  { title: '创建时间', key: 'createdAt', width: 170,
    render: (h, p) => h('span', {}, fmtTime(p.row.createdAt)) },
  { title: '操作', slot: 'action', width: 190 }
]

const assigneeName = (id) => {
  const a = allAssignees.value.find(x => x.id === id)
  return a ? a.cnName : String(id)
}

const memberColumns = [
  { title: '人员', key: 'assigneeId', render: (h, p) => h('span', {}, assigneeName(p.row.assigneeId)) },
  { title: '渠道', key: 'channelType', width: 100, render: (h, p) => h('span', {}, channelText(p.row.channelType)) },
  { title: '服务类型', key: 'serviceTypes', render: (h, p) => h('span', {}, p.row.serviceTypes || '—') },
  { title: '操作', slot: 'action', width: 90 }
]

const fetch = async () => {
  loading.value = true
  try {
    const res = await request.get('/groups', { params: { current: page.value, size: pageSize.value } })
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

const fetchMembers = async () => {
  const res = await request.get(`/groups/${memberGroupId.value}/assignees`)
  memberList.value = res.data || []
}

const fetchAssignees = async () => {
  const res = await request.get('/assignees', { params: { current: 1, size: 1000 } })
  allAssignees.value = res.data?.records || []
}

const openMembers = async (row) => {
  memberGroupId.value = row.id
  memberVisible.value = true
  try {
    await Promise.all([fetchMembers(), fetchAssignees()])
  } catch (e) {
    console.error(e)
  }
}

const addMember = async () => {
  if (!addForm.assigneeId) {
    Message.warning({ content: '请选择人员' })
    return
  }
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: dedupe member add before hitting DB unique constraint
  const exists = memberList.value.some(m => m.assigneeId === addForm.assigneeId && m.channelType === addForm.channelType)
  if (exists) {
    Message.warning({ content: '该人员已在此通信组中（相同渠道）' })
    return
  }
  try {
    await request.post(`/groups/${memberGroupId.value}/assignees`, {
      assigneeId: addForm.assigneeId,
      channelType: addForm.channelType,
      serviceTypes: addForm.serviceTypes
    })
    addForm.assigneeId = null
    addForm.serviceTypes = ''
    fetchMembers()
  } catch (e) {
    console.error(e)
  }
}

const removeMember = async (row) => {
  try {
    await request.delete(`/groups/${memberGroupId.value}/assignees/${row.assigneeId}`, {
      params: { channelType: row.channelType }
    })
    fetchMembers()
  } catch (e) {
    console.error(e)
  }
}

const remove = async (id) => {
  Modal.confirm({
    title: '删除确认',
    content: '确定删除该通信组吗？其成员绑定将一并删除。',
    onOk: async () => {
      await request.delete(`/groups/${id}`)
      Message.success({ content: '已删除' })
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = '新建通信组'
  editingId.value = null
  formData.name = ''
  formData.sendType = ''
  formData.assigneeIds = []
  if (!allAssignees.value.length) fetchAssignees()
  modalVisible.value = true
}

const edit = async (row) => {
  modalTitle.value = '编辑通信组'
  editingId.value = row.id
  formData.name = row.name
  formData.sendType = row.sendType
  formData.assigneeIds = []
  try {
    if (!allAssignees.value.length) await fetchAssignees()
    const res = await request.get(`/groups/${row.id}/assignees`)
    const members = res.data || []
    editMembers.value = members.map(m => ({
      assigneeId: m.assigneeId,
      channelType: m.channelType,
      serviceTypes: m.serviceTypes,
      cnName: assigneeName(m.assigneeId)
    }))
  } catch (e) {
    editMembers.value = []
  }
  modalVisible.value = true
}

const deleteEditMember = async (m) => {
  try {
    await request.delete(`/groups/${editingId.value}/assignees/${m.assigneeId}`, {
      params: { channelType: m.channelType }
    })
    editMembers.value = editMembers.value.filter(x => !(x.assigneeId === m.assigneeId && x.channelType === m.channelType))
  } catch (e) {
    console.error(e)
  }
}

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: create group with selected assignees (default DINGDING)
const handleSubmit = async () => {
  const payload = { name: formData.name, sendType: formData.sendType }
  if (editingId.value) {
    await request.put(`/groups/${editingId.value}`, payload)
  } else {
    const res = await request.post('/groups', payload)
    const groupId = res.data?.id
    if (groupId && formData.assigneeIds.length) {
      for (const assigneeId of formData.assigneeIds) {
        try {
          await request.post(`/groups/${groupId}/assignees`, { assigneeId, channelType: 'DINGDING' })
        } catch (e) {
          console.error(e)
        }
      }
    }
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
.form-tip { margin-left: 10px; font-size: 12px; color: var(--aw-text-faint); }

.member-add { display: flex; gap: 8px; margin-bottom: 14px; }
.member-table { margin-top: 4px; }
.member-empty { text-align: center; color: var(--aw-text-faint); font-size: 13px; padding: 20px 0; }

.member-list { display: flex; flex-direction: column; gap: 6px; }
.member-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 6px 10px;
}
.member-name { font-size: 13px; color: var(--aw-text); }
</style>
