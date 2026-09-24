<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { DEFAULTS, createDisplacement, supportsHtmlInCanvas } from './displacement-engine.js'

const props = defineProps({
  grid:       { type: Number, default: DEFAULTS.grid },
  cellAspect: { type: Number, default: DEFAULTS.cellAspect },
  radius:     { type: Number, default: DEFAULTS.radius },
  strength:   { type: Number, default: DEFAULTS.strength },
  threshold:  { type: Number, default: DEFAULTS.threshold },
  relaxation: { type: Number, default: DEFAULTS.relaxation },
  shift:      { type: Number, default: DEFAULTS.shift },
  aberration: { type: Number, default: DEFAULTS.aberration },
  grain:      { type: Number, default: DEFAULTS.grain },
  grainSize:  { type: Number, default: DEFAULTS.grainSize },
  grainSpeed: { type: Number, default: DEFAULTS.grainSpeed },
  scramble:   { type: Number, default: DEFAULTS.scramble }
})

const sourceEl = ref(null)
const contentEl = ref(null)
const outputEl = ref(null)
const native = ref(supportsHtmlInCanvas()) // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260924: detect support during setup so the slotted login form is never remounted

let instance = null
let disposed = false

onMounted(async () => {
  await nextTick()
  if (disposed) return
  if (sourceEl.value && contentEl.value && outputEl.value) {
    instance = createDisplacement(
      {
        source: sourceEl.value,
        content: contentEl.value,
        output: outputEl.value
      },
      props
    )
    if (native.value && !instance) {
      native.value = false
      await nextTick()
      if (disposed) return
      if (sourceEl.value && contentEl.value && outputEl.value) {
        instance = createDisplacement(
          {
            source: sourceEl.value,
            content: contentEl.value,
            output: outputEl.value
          },
          props
        )
      }
    }
  }
})

onBeforeUnmount(() => {
  disposed = true
  instance?.destroy()
  instance = null
})

watch(
  () => ({ ...props }),
  (next) => instance?.setOptions(next),
  { deep: true }
)
</script>

<template>
  <div class="canvasui-displacement" style="position: relative">
    <canvas
      ref="sourceEl"
      layoutsubtree="true"
      content="drawable"
      :style="
        native
          ? 'position: absolute; inset: 0; width: 100%; height: 100%'
          : 'display: none'
      "
    >
      <!-- overflow: visible, not auto: a scroll container could add a scrollbar that changes the content width, feeding back into the caller's measured height -->
      <div
        v-if="native"
        ref="contentEl"
        drawable
        style="position: relative; width: 100%; height: 100%; overflow: visible"
      >
        <slot />
      </div>
    </canvas>
    <div
      v-if="!native"
      ref="contentEl"
      style="position: relative; width: 100%; height: 100%; overflow: visible"
    >
      <slot />
    </div>
    <canvas
      ref="outputEl"
      aria-hidden="true"
      style="
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        pointer-events: none;
      "
    />
  </div>
</template>
