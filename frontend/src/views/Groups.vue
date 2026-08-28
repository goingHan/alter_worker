<template>
  <div>
    <Card title="Communication Groups">
      <Button type="primary" @click="openCreateModal" class="mb-2">Create Group</Button>
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
          <Input v-model="formData.name" placeholder="Group name" />
        </FormItem>
        <FormItem label="Send Type">
          <Select v-model="formData.sendType" placeholder="Select send type">
            <Option value="service_type">Service Type</Option>
            <Option value="direct">Direct</Option>
          </Select>
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
const modalTitle = ref('Create Group')
const editingId = ref(null)

const formData = reactive({
  name: '',
  sendType: ''
})

const columns = [
  { title: 'Name', key: 'name' },
  { title: 'Send Type', key: 'sendType' },
  { title: 'Created At', key: 'createdAt' },
  { title: 'Action', slot: 'action', width: 150 }
]

const fetch = async () => {
  loading.value = true
  const res = await request.get('/groups')
  data.value = res.data?.records || []
  loading.value = false
}

// #AI-CODE-dev_release/v0.0.1-20260604: add delete confirmation and modal form
const remove = async (id) => {
  Modal.confirm({
    title: 'Confirm Delete',
    content: 'Are you sure you want to delete this group? This action cannot be undone.',
    onOk: async () => {
      await request.delete(`/groups/${id}`)
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = 'Create Group'
  editingId.value = null
  formData.name = ''
  formData.sendType = ''
  modalVisible.value = true
}

const edit = (row) => {
  modalTitle.value = 'Edit Group'
  editingId.value = row.id
  formData.name = row.name
  formData.sendType = row.sendType
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (editingId.value) {
    await request.put(`/groups/${editingId.value}`, formData)
  } else {
    await request.post('/groups', formData)
  }
  modalVisible.value = false
  fetch()
}

onMounted(fetch)
</script>

<style scoped>
.mb-2 { margin-bottom: 12px; }
</style>
