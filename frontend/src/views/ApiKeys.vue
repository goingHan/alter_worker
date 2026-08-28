<template>
  <div>
    <Card title="API Keys">
      <Button type="primary" @click="openCreateModal" class="mb-2">Create API Key</Button>
      <Table :columns="columns" :data="data" :loading="loading">
        <template #action="{ row }">
          <Button size="small" @click="edit(row)">Edit</Button>
          <Button size="small" type="error" @click="remove(row.id)">Delete</Button>
        </template>
      </Table>
    </Card>

    <!-- #AI-CODE-dev_release/v0.0.1-20260604: create/edit modal -->
    <Modal v-model="modalVisible" :title="modalTitle" @on-ok="handleSubmit">
      <Form :model="formData" :label-width="100">
        <FormItem label="Name">
          <Input v-model="formData.name" placeholder="API Key name" />
        </FormItem>
        <FormItem label="Rate Limit">
          <InputNumber v-model="formData.rateLimit" :min="1" :max="10000" style="width: 100%" />
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
const modalTitle = ref('Create API Key')
const editingId = ref(null)

const formData = reactive({
  name: '',
  rateLimit: 100
})

const columns = [
  { title: 'Name', key: 'name' },
  { title: 'Rate Limit', key: 'rateLimit' },
  { title: 'Created At', key: 'createdAt' },
  { title: 'Action', slot: 'action', width: 150 }
]

const fetch = async () => {
  loading.value = true
  const res = await request.get('/api-keys')
  data.value = res.data?.records || []
  loading.value = false
}

// #AI-CODE-dev_release/v0.0.1-20260604: add delete confirmation and modal form
const remove = async (id) => {
  Modal.confirm({
    title: 'Confirm Delete',
    content: 'Are you sure you want to delete this API key? This action cannot be undone.',
    onOk: async () => {
      await request.delete(`/api-keys/${id}`)
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = 'Create API Key'
  editingId.value = null
  formData.name = ''
  formData.rateLimit = 100
  modalVisible.value = true
}

const edit = (row) => {
  modalTitle.value = 'Edit API Key'
  editingId.value = row.id
  formData.name = row.name
  formData.rateLimit = row.rateLimit
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (editingId.value) {
    await request.put(`/api-keys/${editingId.value}`, formData)
  } else {
    await request.post('/api-keys', formData)
  }
  modalVisible.value = false
  fetch()
}

onMounted(fetch)
</script>

<style scoped>
.mb-2 { margin-bottom: 12px; }
</style>
