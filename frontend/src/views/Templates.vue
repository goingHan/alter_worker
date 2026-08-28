<template>
  <div>
    <Card title="Message Templates">
      <Button type="primary" @click="openCreateModal" class="mb-2">Create Template</Button>
      <Table :columns="columns" :data="data" :loading="loading">
        <template #action="{ row }">
          <Button size="small" @click="preview(row)">Preview</Button>
          <Button size="small" @click="edit(row)">Edit</Button>
          <Button size="small" type="error" @click="remove(row.id)">Delete</Button>
        </template>
      </Table>
    </Card>

    <!-- #AI-CODE-dev_release/v0.0.1-20260604: create/edit modal -->
    <Modal v-model="modalVisible" :title="modalTitle" @on-ok="handleSubmit">
      <Form :model="formData" :label-width="100">
        <FormItem label="Name">
          <Input v-model="formData.name" placeholder="Template name" />
        </FormItem>
        <FormItem label="Channel Type">
          <Select v-model="formData.channelType" placeholder="Select channel type">
            <Option value="DINGDING">DingDing</Option>
            <Option value="EMAIL">Email</Option>
          </Select>
        </FormItem>
        <FormItem label="Content">
          <Input v-model="formData.content" type="textarea" :rows="6" placeholder="Jinjava template content" />
        </FormItem>
        <FormItem label="Is Default">
          <Checkbox v-model="formData.isDefault">Default template</Checkbox>
        </FormItem>
      </Form>
    </Modal>

    <!-- Preview Modal -->
    <Modal v-model="previewVisible" title="Template Preview" :footer-hide="true">
      <pre>{{ previewContent }}</pre>
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
const modalTitle = ref('Create Template')
const editingId = ref(null)
const previewVisible = ref(false)
const previewContent = ref('')

const formData = reactive({
  name: '',
  channelType: '',
  content: '',
  isDefault: false
})

const columns = [
  { title: 'Name', key: 'name' },
  { title: 'Channel Type', key: 'channelType' },
  { title: 'Default', key: 'isDefault' },
  { title: 'Action', slot: 'action', width: 200 }
]

const fetch = async () => {
  loading.value = true
  const res = await request.get('/templates')
  data.value = res.data?.records || []
  loading.value = false
}

// #AI-CODE-dev_release/v0.0.1-20260604: add delete confirmation, preview modal, modal form
const preview = async (row) => {
  try {
    const res = await request.post(`/templates/${row.id}/preview`, { title: 'Test Alert', content: 'This is a test' })
    previewContent.value = res.data
    previewVisible.value = true
  } catch (e) {
    Modal.error({ title: 'Preview Failed', content: 'Failed to render template' })
  }
}

const remove = async (id) => {
  Modal.confirm({
    title: 'Confirm Delete',
    content: 'Are you sure you want to delete this template? This action cannot be undone.',
    onOk: async () => {
      await request.delete(`/templates/${id}`)
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = 'Create Template'
  editingId.value = null
  formData.name = ''
  formData.channelType = ''
  formData.content = ''
  formData.isDefault = false
  modalVisible.value = true
}

const edit = (row) => {
  modalTitle.value = 'Edit Template'
  editingId.value = row.id
  formData.name = row.name
  formData.channelType = row.channelType
  formData.content = row.content || ''
  formData.isDefault = row.isDefault || false
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (editingId.value) {
    await request.put(`/templates/${editingId.value}`, formData)
  } else {
    await request.post('/templates', formData)
  }
  modalVisible.value = false
  fetch()
}

onMounted(fetch)
</script>

<style scoped>
.mb-2 { margin-bottom: 12px; }
</style>
