<template>
  <div>
    <Card title="Assignees">
      <Button type="primary" @click="openCreateModal" class="mb-2">Create Assignee</Button>
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
        <FormItem label="CN Name">
          <Input v-model="formData.cnName" placeholder="Chinese name" />
        </FormItem>
        <FormItem label="Phone">
          <Input v-model="formData.phone" placeholder="Phone number" />
        </FormItem>
        <FormItem label="DingDing Username">
          <Input v-model="formData.dingdingUsername" placeholder="DingDing username" />
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
const modalTitle = ref('Create Assignee')
const editingId = ref(null)

const formData = reactive({
  cnName: '',
  phone: '',
  dingdingUsername: ''
})

const columns = [
  { title: 'CN Name', key: 'cnName' },
  { title: 'Phone', key: 'phone' },
  { title: 'DingDing', key: 'dingdingUsername' },
  { title: 'Action', slot: 'action', width: 150 }
]

const fetch = async () => {
  loading.value = true
  const res = await request.get('/assignees')
  data.value = res.data?.records || []
  loading.value = false
}

// #AI-CODE-dev_release/v0.0.1-20260604: add delete confirmation and modal form
const remove = async (id) => {
  Modal.confirm({
    title: 'Confirm Delete',
    content: 'Are you sure you want to delete this assignee? This action cannot be undone.',
    onOk: async () => {
      await request.delete(`/assignees/${id}`)
      fetch()
    }
  })
}

const openCreateModal = () => {
  modalTitle.value = 'Create Assignee'
  editingId.value = null
  formData.cnName = ''
  formData.phone = ''
  formData.dingdingUsername = ''
  modalVisible.value = true
}

const edit = (row) => {
  modalTitle.value = 'Edit Assignee'
  editingId.value = row.id
  formData.cnName = row.cnName
  formData.phone = row.phone || ''
  formData.dingdingUsername = row.dingdingUsername || ''
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (editingId.value) {
    await request.put(`/assignees/${editingId.value}`, formData)
  } else {
    await request.post('/assignees', formData)
  }
  modalVisible.value = false
  fetch()
}

onMounted(fetch)
</script>

<style scoped>
.mb-2 { margin-bottom: 12px; }
</style>
