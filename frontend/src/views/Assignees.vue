// #AI-CODE-FILE-dev_release/v0.0.1-20260901: unify to green-sorrow theme + Chinese UI + pagination
<template>
  <div>
    <Card :bordered="false" class="table-card">
      <div class="card-header">
        <span class="card-title">值班人员</span>
        <Button type="primary" size="small" @click="openCreateModal">
          <Icon type="ios-add" /> 新建人员
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
      <Form :model="formData" :label-width="100">
        <FormItem label="中文姓名">
          <Input v-model="formData.cnName" placeholder="中文姓名（必填）" />
        </FormItem>
        <FormItem label="手机号">
          <Input v-model="formData.phone" placeholder="钉钉绑定手机号" />
          <div class="form-tip">钉钉机器人只能按手机号 @ 人，请填写该成员的钉钉绑定手机号</div>
        </FormItem>
        <FormItem label="钉钉姓名">
          <Input v-model="formData.dingdingUsername" placeholder="钉钉姓名（仅模板展示用）" />
          <div class="form-tip">仅用于模板变量展示，不参与 @</div>
        </FormItem>
        <FormItem label="邮箱">
          <Input v-model="formData.email" placeholder="邮箱（用于邮件通知）" />
        </FormItem>
        <FormItem label="群组">
          <Select v-model="formData.groupIds" multiple filterable placeholder="选择群组">
            <Option v-for="g in allGroups" :key="g.id" :value="g.id">{{ g.name }}</Option>
          </Select>
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Modal, Message } from 'view-ui-plus'
import request from '../api/request.js'

const loading = ref(false)
const data = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(15)
const modalVisible = ref(false)
const modalTitle = ref('新建人员')
const editingId = ref(null)
const allGroups = ref([])
const groupNamesMap = ref({})
const originalGroupIds = ref([])

const formData = reactive({ cnName: '', phone: '', dingdingUsername: '', email: '', groupIds: [] })

const columns = [
  { title: '中文姓名', key: 'cnName' },
  { title: '手机号', key: 'phone' },
  { title: '钉钉姓名', key: 'dingdingUsername' },
  { title: '群组', key: 'groups', render: (h, p) => {
    const names = groupNamesMap.value[p.row.id] || []
    return h('span', {}, names.length ? names.join('、') : '—')
  } },
  { title: '创建时间', key: 'createdAt', width: 170 },
  { title: '操作', slot: 'action', width: 140 }
]

const fetch = async () => {
  loading.value = true
  try {
    const res = await request.get('/assignees', { params: { current: page.value, size: pageSize.value } })
    data.value = res.data?.records || []
    total.value = res.data?.total || 0
    fetchGroupMemberships()
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

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: load groups for the assignee form select
const fetchGroups = async () => {
  const res = await request.get('/groups', { params: { current: 1, size: 1000 } })
  allGroups.value = res.data?.records || []
}

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: load group names per assignee for the table column
const fetchGroupMemberships = async () => {
  try {
    const res = await request.get('/assignees/groups')
    const map = {}
    ;(res.data || []).forEach(m => {
      if (!map[m.assigneeId]) map[m.assigneeId] = []
      map[m.assigneeId].push(m.groupName || m.groupId)
    })
    groupNamesMap.value = map
  } catch (e) {
    console.error(e)
  }
}

const remove = async (id) => {
  Modal.confirm({
    title: '删除确认',
    content: '确定删除该值班人员吗？此操作不可撤销。',
    onOk: async () => {
      await request.delete(`/assignees/${id}`)
      Message.success({ content: '已删除' })
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = '新建人员'
  editingId.value = null
  formData.cnName = ''
  formData.phone = ''
  formData.dingdingUsername = ''
  formData.email = ''
  formData.groupIds = []
  originalGroupIds.value = []
  if (!allGroups.value.length) fetchGroups()
  modalVisible.value = true
}

const edit = async (row) => {
  modalTitle.value = '编辑人员'
  editingId.value = row.id
  formData.cnName = row.cnName
  formData.phone = row.phone || ''
  formData.dingdingUsername = row.dingdingUsername || ''
  formData.email = row.email || ''
  if (!allGroups.value.length) await fetchGroups()
  try {
    const res = await request.get(`/assignees/${row.id}/groups`)
    const groups = res.data || []
    formData.groupIds = groups.map(g => g.groupId)
    originalGroupIds.value = groups.map(g => g.groupId)
  } catch (e) {
    formData.groupIds = []
    originalGroupIds.value = []
  }
  modalVisible.value = true
}

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: sync assignee->group memberships on save (diff for edit)
const handleSubmit = async () => {
  const payload = {
    cnName: formData.cnName,
    phone: formData.phone,
    dingdingUsername: formData.dingdingUsername,
    email: formData.email
  }
  if (editingId.value) {
    await request.put(`/assignees/${editingId.value}`, payload)
    const toAdd = formData.groupIds.filter(id => !originalGroupIds.value.includes(id))
    const toRemove = originalGroupIds.value.filter(id => !formData.groupIds.includes(id))
    for (const gid of toAdd) {
      try {
        await request.post(`/assignees/${editingId.value}/groups`, { groupId: gid })
      } catch (e) {
        console.error(e)
      }
    }
    for (const gid of toRemove) {
      try {
        await request.delete(`/assignees/${editingId.value}/groups/${gid}`)
      } catch (e) {
        console.error(e)
      }
    }
  } else {
    const res = await request.post('/assignees', payload)
    const assigneeId = res.data?.id
    if (assigneeId && formData.groupIds.length) {
      for (const gid of formData.groupIds) {
        try {
          await request.post(`/assignees/${assigneeId}/groups`, { groupId: gid })
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
.form-tip { font-size: 12px; color: var(--aw-text-faint); margin-top: 4px; }
</style>
