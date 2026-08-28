<template>
  <div>
    <Card title="Alert Rules">
      <Button type="primary" @click="openCreateModal" class="mb-2">Create Rule</Button>
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
          <Input v-model="formData.name" placeholder="Rule name" />
        </FormItem>
        <FormItem label="Alert Type">
          <Select v-model="formData.alertType" placeholder="Select alert type">
            <Option value="PROMETHEUS">Prometheus</Option>
            <Option value="JSON">JSON</Option>
          </Select>
        </FormItem>
        <FormItem label="Match Key">
          <Input v-model="formData.matchKey" placeholder="Label or key name to match" />
        </FormItem>
        <FormItem label="Match Value">
          <Input v-model="formData.matchValue" placeholder="Value to match" />
        </FormItem>
        <FormItem label="Retry Max">
          <InputNumber v-model="formData.retryMax" :min="1" :max="10" style="width: 100%" />
        </FormItem>
        <FormItem label="Retry Backoff">
          <Input v-model="formData.retryBackoff" placeholder="e.g., 5s,10s,20s" />
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
const modalTitle = ref('Create Rule')
const editingId = ref(null)

const formData = reactive({
  name: '',
  alertType: '',
  matchKey: '',
  matchValue: '',
  retryMax: 3,
  retryBackoff: '5s,10s,20s'
})

const columns = [
  { title: 'Name', key: 'name' },
  { title: 'Alert Type', key: 'alertType' },
  { title: 'Match Key', key: 'matchKey' },
  { title: 'Retry Max', key: 'retryMax' },
  { title: 'Action', slot: 'action', width: 150 }
]

const fetch = async () => {
  loading.value = true
  const res = await request.get('/rules')
  data.value = res.data?.records || []
  loading.value = false
}

// #AI-CODE-dev_release/v0.0.1-20260604: add delete confirmation and modal form
const remove = async (id) => {
  Modal.confirm({
    title: 'Confirm Delete',
    content: 'Are you sure you want to delete this rule? This action cannot be undone.',
    onOk: async () => {
      await request.delete(`/rules/${id}`)
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = 'Create Rule'
  editingId.value = null
  formData.name = ''
  formData.alertType = ''
  formData.matchKey = ''
  formData.matchValue = ''
  formData.retryMax = 3
  formData.retryBackoff = '5s,10s,20s'
  modalVisible.value = true
}

const edit = (row) => {
  modalTitle.value = 'Edit Rule'
  editingId.value = row.id
  formData.name = row.name
  formData.alertType = row.alertType
  formData.matchKey = row.matchKey || ''
  formData.matchValue = row.matchValue || ''
  formData.retryMax = row.retryMax || 3
  formData.retryBackoff = row.retryBackoff || '5s,10s,20s'
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (editingId.value) {
    await request.put(`/rules/${editingId.value}`, formData)
  } else {
    await request.post('/rules', formData)
  }
  modalVisible.value = false
  fetch()
}

onMounted(fetch)
</script>

<style scoped>
.mb-2 { margin-bottom: 12px; }
</style>
