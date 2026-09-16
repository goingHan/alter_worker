<template>
  <div class="line-chart" ref="wrap">
    <div class="chart-legend">
      <span v-for="s in series" :key="s.name" class="legend-item">
        <i class="legend-dot" :style="{ background: s.color }"></i>{{ s.name }}
      </span>
    </div>
    <div class="chart-body">
      <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" class="chart-svg"
           :style="{ height: height + 'px' }">
        <defs>
          <linearGradient v-for="s in series" :key="'g-' + s.name" :id="`area-${s.name}`"
                          x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" :stop-color="s.color" stop-opacity="0.22" />
            <stop offset="100%" :stop-color="s.color" stop-opacity="0.01" />
          </linearGradient>
        </defs>
        <g v-for="g in gridLines" :key="g.y">
          <line :x1="padL" :y1="g.y" :x2="W - padR" :y2="g.y" class="grid-line" />
          <text :x="padL - 8" :y="g.y + 4" text-anchor="end" class="axis-text">{{ g.label }}</text>
        </g>
        <line :x1="padL" :y1="H - padB" :x2="W - padR" :y2="H - padB" class="axis-line" />

        <g v-for="(s, si) in series" :key="s.name">
          <polygon v-if="si === 0 && s.data.some(v => v > 0)" :points="areaPoints(s)" :fill="areaFill(s)" />
          <polyline :points="linePoints(s)" fill="none" :stroke="s.color" stroke-width="2.2"
                    stroke-linejoin="round" stroke-linecap="round" />
        </g>

        <g v-for="(l, i) in xLabels" :key="i">
          <text :x="xOf(i * labelStep)" :y="H - padB + 18" text-anchor="middle" class="axis-text">{{ l }}</text>
        </g>

        <line v-if="hoverX !== null" :x1="hoverX" :y1="padT" :x2="hoverX" :y2="H - padB" class="hover-line" />
        <circle v-for="pt in hoverPoints" :key="pt.key" :cx="pt.x" :cy="pt.y" :r="4" :fill="pt.color"
                stroke="var(--aw-card)" stroke-width="1.5" />
      </svg>

      <div v-if="hoverX !== null" class="chart-tooltip" :style="tooltipStyle">
        <div class="tooltip-title">{{ hoverLabel }}</div>
        <div v-for="s in series" :key="s.name" class="tooltip-row">
          <i class="legend-dot" :style="{ background: s.color }"></i>
          <span>{{ s.name }}</span>
          <b>{{ hoverIdx !== null && s.data[hoverIdx] != null ? s.data[hoverIdx] : '-' }}</b>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  labels: { type: Array, default: () => [] },
  series: { type: Array, default: () => [] },
  height: { type: Number, default: 300 }
})

const wrap = ref(null)
const hoverX = ref(null)
const hoverIdx = ref(null)
const hoverLabel = ref('')

const padL = 52
const padR = 16
const padT = 14
const padB = 34
const H = props.height
const W = 860

const labelStep = computed(() => Math.max(1, Math.ceil(props.labels.length / 6)))

const maxValue = computed(() => {
  let max = 0
  for (const s of props.series) {
    for (const v of s.data || []) {
      if (v != null && v > max) max = v
    }
  }
  return max || 10
})

const niceMax = computed(() => {
  const raw = maxValue.value
  const mag = Math.pow(10, Math.floor(Math.log10(raw)))
  const norm = raw / mag
  const nice = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10
  return nice * mag
})

const gridLines = computed(() => {
  const lines = []
  const steps = 5
  for (let i = 0; i <= steps; i++) {
    const value = (niceMax.value / steps) * i
    const y = yOf(value)
    lines.push({ y, label: fmt(value) })
  }
  return lines
})

const xLabels = computed(() => {
  const labels = []
  for (let i = 0; i < props.labels.length; i += labelStep.value) {
    labels.push(props.labels[i])
  }
  return labels
})

const xOf = (i) => {
  const n = Math.max(1, props.labels.length - 1)
  return padL + (i / n) * (W - padL - padR)
}

const yOf = (v) => {
  const span = H - padT - padB
  return H - padB - (v / niceMax.value) * span
}

const linePoints = (s) => {
  return (s.data || []).map((v, i) => {
    if (v == null) return null
    return `${xOf(i)},${yOf(v)}`
  }).filter(Boolean).join(' ')
}

const areaPoints = (s) => {
  const pts = (s.data || []).map((v, i) => v == null ? null : `${xOf(i)},${yOf(v)}`)
    .filter(Boolean).join(' ')
  if (!pts) return ''
  const last = (s.data || []).map((v, i) => v == null ? -1 : i).filter(i => i >= 0).pop()
  return `${padL},${H - padB} ${pts} ${xOf(last)},${H - padB}`
}

const areaFill = (s) => {
  return `url(#area-${s.name})`
}

const hoverPoints = computed(() => {
  if (hoverIdx.value === null) return []
  return props.series.filter(s => s.data[hoverIdx.value] != null).map(s => ({
    key: s.name,
    x: xOf(hoverIdx.value),
    y: yOf(s.data[hoverIdx.value]),
    color: s.color
  }))
})

const tooltipStyle = computed(() => {
  if (hoverX.value === null) return {}
  const left = Math.min(Math.max(hoverX.value + 14, 60), W - 170)
  return { left: `${(left / W) * 100}%` }
})

const fmt = (v) => {
  if (v >= 1000000) return (v / 1000000).toFixed(1) + 'M'
  if (v >= 1000) return (v / 1000).toFixed(1) + 'k'
  return String(Math.round(v * 10) / 10)
}

let hoverTarget = null

const onMouseMove = (e) => {
  const rect = wrap.value.getBoundingClientRect()
  const svgX = ((e.clientX - rect.left) / rect.width) * W
  if (svgX < padL || svgX > W - padR) {
    hoverX.value = null
    hoverIdx.value = null
    return
  }
  const n = Math.max(1, props.labels.length - 1)
  const rawIdx = ((svgX - padL) / (W - padL - padR)) * n
  const idx = Math.round(rawIdx)
  hoverIdx.value = Math.min(Math.max(idx, 0), props.labels.length - 1)
  hoverX.value = xOf(hoverIdx.value)
  hoverLabel.value = props.labels[hoverIdx.value] || ''
}

const onMouseLeave = () => {
  hoverX.value = null
  hoverIdx.value = null
}

onMounted(() => {
  hoverTarget = wrap.value.querySelector('.chart-body')
  hoverTarget.addEventListener('mousemove', onMouseMove)
  hoverTarget.addEventListener('mouseleave', onMouseLeave)
})

onUnmounted(() => {
  if (hoverTarget) {
    hoverTarget.removeEventListener('mousemove', onMouseMove)
    hoverTarget.removeEventListener('mouseleave', onMouseLeave)
  }
})
</script>

<style scoped>
.line-chart {
  position: relative;
  width: 100%;
}

.chart-legend {
  display: flex;
  gap: 20px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--aw-text-secondary);
}

.legend-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  display: inline-block;
}

.chart-body {
  position: relative;
  width: 100%;
}

.chart-svg {
  display: block;
  width: 100%;
  height: 100%;
}

.grid-line {
  stroke: var(--aw-border);
  stroke-width: 1;
  stroke-dasharray: 3 4;
}

.axis-line {
  stroke: var(--aw-border);
  stroke-width: 1;
}

.axis-text {
  font-size: 11px;
  fill: var(--aw-text-faint);
}

.hover-line {
  stroke: var(--aw-text-faint);
  stroke-width: 1;
  stroke-dasharray: 2 3;
}

.chart-tooltip {
  position: absolute;
  top: 8px;
  transform: translateX(0);
  background: var(--aw-panel-hover);
  border: 1px solid var(--aw-border);
  border-radius: 8px;
  padding: 8px 12px;
  box-shadow: var(--aw-shadow);
  pointer-events: none;
  min-width: 130px;
}

.tooltip-title {
  font-size: 12px;
  color: var(--aw-text-secondary);
  margin-bottom: 4px;
}

.tooltip-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--aw-text);
}

.tooltip-row span { flex: 1; }
.tooltip-row b { color: var(--aw-accent); }
</style>
