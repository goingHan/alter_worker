<template>
  <div>
    <Card :bordered="false" class="config-card">
      <div class="card-header">
        <span class="card-title">告警汇总（WARNING / INFO）</span>
        <Button size="small" @click="fetchConfig" :loading="loading">
          <Icon type="ios-refresh" /> 刷新
        </Button>
      </div>

      <div class="intro-tip">
        开启后，WARNING / INFO 级别的告警不再逐条发送，而是按下方频率汇总成一个 HTML 页面统一推送；关闭后这些告警恢复为逐条即时发送。
        通知人不在此配置——由各告警规则自身的联系人（通信组 / 值班人员）合并去重后接收。
        若某条规则需要在 WARNING / INFO 级别也立即发送、不参与汇总，请在该规则的「忽略聚合」开关中处理。
      </div>

      <Form :model="formData" :label-width="110">
        <FormItem label="启用汇总">
          <i-switch v-model="formData.enabled" />
          <span class="form-tip">{{ formData.enabled ? 'WARNING / INFO 告警将汇总发送' : 'WARNING / INFO 告警逐条即时发送' }}</span>
        </FormItem>
        <FormItem label="Cron 表达式">
          <Input v-model="formData.cronExpr" placeholder="0 0 * * * *" class="cron-input" />
          <div class="form-tip">按此频率汇总并发送一次（可小时级）。例如 0 0 * * * * 表示每小时整点</div>
        </FormItem>
        <FormItem label="汇总模板">
          <Select v-model="formData.templateId" clearable filterable placeholder="留空使用内置 WARNING 汇总模板">
            <Option v-for="t in allTemplates" :key="t.id" :value="t.id">{{ t.name }}{{ t.format === 'HTML' ? '（HTML）' : '' }}</Option>
          </Select>
        </FormItem>
        <FormItem v-if="nextRunAt" label="下次执行">
          <span class="run-info">{{ nextRunAt }}</span>
        </FormItem>
        <FormItem v-if="lastRunAt" label="上次执行">
          <span class="run-info">{{ lastRunAt }}</span>
        </FormItem>
        <FormItem>
          <Button type="primary" :loading="saving" @click="save">保存</Button>
          <Button :loading="running" @click="runOnce" class="run-btn">立即生成并发送</Button>
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
const allTemplates = ref([])
const nextRunAt = ref('')
const lastRunAt = ref('')

const formData = reactive({ name: 'WARNING 告警汇总', cronExpr: '', enabled: false, templateId: null })

const loadTemplates = async () => {
  try {
    const res = await request.get('/templates', { params: { current: 1, size: 1000 } })
    allTemplates.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  }
}

const configJson = () => JSON.stringify({ templateId: formData.templateId || null })

const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await request.get('/scheduled-jobs', { params: { jobType: 'WARNING_DIGEST' } })
    const job = res.data
    if (job && job.cronExpr) {
      formData.name = job.name || 'WARNING 告警汇总'
      formData.cronExpr = job.cronExpr
      formData.enabled = job.enabled === true
      let templateId = null
      try {
        const cfg = job.configJson ? JSON.parse(job.configJson) : null
        if (cfg && cfg.templateId) templateId = cfg.templateId
      } catch (e) {
        // invalid configJson falls back to the built-in template server-side
      }
      formData.templateId = templateId
      nextRunAt.value = job.nextRunAt ? fmtTime(job.nextRunAt) : ''
      lastRunAt.value = job.lastRunAt ? fmtTime(job.lastRunAt) : ''
    } else {
      formData.name = 'WARNING 告警汇总'
      formData.cronExpr = ''
      formData.enabled = false
      formData.templateId = null
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
      jobType: 'WARNING_DIGEST',
      name: formData.name,
      cronExpr: formData.cronExpr.trim(),
      enabled: formData.enabled === true,
      configJson: configJson()
    })
    Message.success({ content: '已保存' })
    fetchConfig()
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const runReasonText = (reason) => {
  const map = {
    'no-data': '当前没有待汇总的 WARNING 告警',
    'no-recipients': '未解析到接收人/渠道（请检查相关规则的联系人配置）',
    'no-template': '未找到可用模板'
  }
  return map[reason] || reason || '未知原因'
}

const runOnce = async () => {
  running.value = true
  try {
    const res = await processorRequest.post('/warning-digest/run', { configJson: configJson() })
    const data = res.data || {}
    if (data.sent) {
      Message.success({ content: `汇总已生成并发送（${data.alertCount} 条告警 / ${data.recipientCount} 个接收人）` })
    } else {
      Message.warning({ content: `未发送：${runReasonText(data.reason)}`, duration: 6 })
    }
    fetchConfig()
  } catch (e) {
    Message.error({ content: e.response?.data?.message || '生成失败' })
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadTemplates(), fetchConfig()])
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
.run-btn { margin-left: 8px; }
.run-info { font-family: Consolas, monospace; font-size: 12px; color: var(--aw-text-secondary); }
</style>
