// #AI-CODE-FILE-dev_release/v0.0.1-20261008: periodic test-message job config page (scheduled_job / TEST_MESSAGE)
<template>
  <div>
    <Card :bordered="false" class="config-card">
      <div class="card-header">
        <span class="card-title">周期测试消息</span>
        <Button size="small" @click="fetchConfig" :loading="loading">
          <Icon type="ios-refresh" /> 刷新
        </Button>
      </div>

      <div class="intro-tip">
        按 Cron 周期向消息渠道发送一条测试消息，用于验证渠道连通性；目标渠道留空时向全部启用渠道各发送一条。
      </div>

      <Form :model="formData" :label-width="110">
        <FormItem label="Cron 表达式">
          <Input v-model="formData.cronExpr" placeholder="0 0 9 * * *" class="cron-input" />
        </FormItem>
        <FormItem label="启用">
          <i-switch v-model="formData.enabled" />
        </FormItem>
        <FormItem label="目标渠道">
          <Select v-model="formData.channelIds" multiple filterable placeholder="留空 = 全部启用渠道" class="channel-select">
            <Option v-for="c in allChannels" :key="c.id" :value="c.id">{{ c.name }}（{{ channelTypeText(c.channelType) }}）</Option>
          </Select>
          <div class="form-tip">留空时向全部启用渠道各发送一条测试消息</div>
        </FormItem>
        <FormItem v-if="nextRunAt" label="下次执行">
          <span class="run-info">{{ nextRunAt }}</span>
        </FormItem>
        <FormItem v-if="lastRunAt" label="上次执行">
          <span class="run-info">{{ lastRunAt }}</span>
        </FormItem>
        <FormItem>
          <Button type="primary" :loading="saving" @click="save">保存</Button>
          <Button :loading="running" @click="runOnce" class="run-btn">立即执行一次</Button>
        </FormItem>
      </Form>
    </Card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Message } from 'view-ui-plus'
import request, { processorRequest } from '../api/request.js'
import { fmtTime } from '../utils/time.js'

const loading = ref(false)
const saving = ref(false)
const running = ref(false)
const allChannels = ref([])
const nextRunAt = ref('')
const lastRunAt = ref('')

const formData = reactive({ name: '周期测试消息', cronExpr: '', enabled: false, channelIds: [] })

const channelTypeText = (t) => (t === 'EMAIL' ? '邮件' : t === 'DINGDING' ? '钉钉' : (t || '--'))

const loadChannels = async () => {
  try {
    const res = await request.get('/channels', { params: { current: 1, size: 1000 } })
    allChannels.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  }
}

// #AI-CODE-FUNCTION-dev_release/v0.0.1-20261008: GET returns the raw ScheduledJob row (or an empty body when the
// job does not exist yet), so treat a blank body as "no config, show empty form"
const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await request.get('/scheduled-jobs', { params: { jobType: 'TEST_MESSAGE' } })
    const job = res.data
    if (job && job.cronExpr) {
      formData.name = job.name || '周期测试消息'
      formData.cronExpr = job.cronExpr
      formData.enabled = job.enabled === true
      let ids = []
      try {
        const cfg = job.configJson ? JSON.parse(job.configJson) : null
        if (cfg && Array.isArray(cfg.channelIds)) ids = cfg.channelIds
      } catch (e) {
        // invalid configJson falls back to all active channels server-side
      }
      formData.channelIds = ids
      nextRunAt.value = job.nextRunAt ? fmtTime(job.nextRunAt) : ''
      lastRunAt.value = job.lastRunAt ? fmtTime(job.lastRunAt) : ''
    } else {
      formData.name = '周期测试消息'
      formData.cronExpr = ''
      formData.enabled = false
      formData.channelIds = []
      nextRunAt.value = ''
      lastRunAt.value = ''
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!formData.cronExpr || !formData.cronExpr.trim()) {
    Message.warning({ content: '请填写 Cron 表达式' })
    return
  }
  saving.value = true
  try {
    await request.put('/scheduled-jobs', {
      jobType: 'TEST_MESSAGE',
      name: formData.name,
      cronExpr: formData.cronExpr.trim(),
      enabled: formData.enabled === true,
      configJson: JSON.stringify({ channelIds: formData.channelIds || [] })
    })
    Message.success({ content: '已保存' })
    fetchConfig()
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const runOnce = async () => {
  running.value = true
  try {
    await processorRequest.post('/jobs/run', {
      jobType: 'TEST_MESSAGE',
      name: formData.name,
      cronExpr: formData.cronExpr ? formData.cronExpr.trim() : null,
      configJson: JSON.stringify({ channelIds: formData.channelIds || [] })
    })
    Message.success({ content: '已触发一次测试消息' })
    fetchConfig()
  } catch (e) {
    Message.error({ content: e.response?.data?.message || '触发失败' })
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadChannels(), fetchConfig()])
})
</script>

<style scoped>
.config-card { border-radius: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-title { font-size: 15px; font-weight: 600; color: var(--aw-text); }

.intro-tip {
  background: var(--aw-panel);
  border: 1px solid var(--aw-border-light);
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--aw-text-secondary);
  line-height: 1.7;
  margin-bottom: 16px;
}

.form-tip { margin-left: 10px; font-size: 12px; color: var(--aw-text-faint); }
.cron-input { max-width: 420px; }
.channel-select { max-width: 560px; }
.run-btn { margin-left: 8px; }
.run-info { font-family: Consolas, monospace; font-size: 12px; color: var(--aw-text-secondary); }
</style>
