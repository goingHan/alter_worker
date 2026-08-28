<template>
  <div>
    <Card title="Message Channels">
      <Button type="primary" @click="openCreateModal" class="mb-2">Create Channel</Button>
      <Table :columns="columns" :data="data" :loading="loading">
        <template #action="{ row }">
          <Button size="small" @click="testChannel(row.id)">Test</Button>
          <Button size="small" @click="edit(row)">Edit</Button>
          <Button size="small" type="error" @click="remove(row.id)">Delete</Button>
        </template>
      </Table>
    </Card>

    <!-- #AI-CODE-dev_release/v0.0.1-20260604: create/edit modal -->
    <Modal v-model="modalVisible" :title="modalTitle" @on-ok="handleSubmit">
      <Form :model="formData" :label-width="100">
        <FormItem label="Name">
          <Input v-model="formData.name" placeholder="Channel name" />
        </FormItem>
        <FormItem label="Channel Type">
          <Select v-model="formData.channelType" placeholder="Select channel type">
            <Option value="DINGDING">DingDing</Option>
            <Option value="EMAIL">Email</Option>
          </Select>
        </FormItem>
        <FormItem label="Config JSON">
          <Input v-model="formData.configJson" type="textarea" :rows="5" :placeholder="configJsonPlaceholder" />
          <div class="config-hint">
            DingDing: { "webhookUrl": "…", "secret": "加签密钥(可选)", "msgType": "text|markdown|action_card", "keyword": ["自定义关键字(可选,发送时自动前置)"] }
            <br />
            Email: { "from": "…", "subject": "…", "format": "html|text" }
          </div>
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
// #AI-CODE-dev_release/v0.0.1-20260805: fix import - iview -> view-ui-plus (Vue 3)
import { Modal } from 'view-ui-plus'
import request from '../api/request.js'

const loading = ref(false)
const data = ref([])
const modalVisible = ref(false)
const modalTitle = ref('Create Channel')
const editingId = ref(null)

const configJsonPlaceholder = '{ "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxx", "secret": "SECxxx", "msgType": "text", "keyword": ["告警"] }'

const formData = reactive({
  name: '',
  channelType: '',
  configJson: ''
})

const columns = [
  { title: 'Name', key: 'name' },
  { title: 'Channel Type', key: 'channelType' },
  { title: 'Created At', key: 'createdAt' },
  { title: 'Action', slot: 'action', width: 200 }
]

const fetch = async () => {
  loading.value = true
  const res = await request.get('/channels')
  data.value = res.data?.records || []
  loading.value = false
}

// #AI-CODE-dev_release/v0.0.1-20260604: add delete confirmation, test feedback, modal form
const testChannel = async (id) => {
  try {
    await request.post(`/channels/${id}/test`)
    Modal.success({ title: 'Success', content: 'Test message sent successfully' })
  } catch (e) {
    Modal.error({ title: 'Failed', content: 'Test message failed to send' })
  }
}

const remove = async (id) => {
  Modal.confirm({
    title: 'Confirm Delete',
    content: 'Are you sure you want to delete this channel? This action cannot be undone.',
    onOk: async () => {
      await request.delete(`/channels/${id}`)
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = 'Create Channel'
  editingId.value = null
  formData.name = ''
  formData.channelType = ''
  formData.configJson = ''
  modalVisible.value = true
}

const edit = (row) => {
  modalTitle.value = 'Edit Channel'
  editingId.value = row.id
  formData.name = row.name
  formData.channelType = row.channelType
  formData.configJson = row.configJson || ''
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (editingId.value) {
    await request.put(`/channels/${editingId.value}`, formData)
  } else {
    await request.post('/channels', formData)
  }
  modalVisible.value = false
  fetch()
}

onMounted(fetch)
</script>

<style scoped>
.mb-2 { margin-bottom: 12px; }
.config-hint { color: #808695; font-size: 12px; line-height: 1.6; margin-top: 4px; }
</style>
