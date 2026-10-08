<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { DEFAULTS, createDisplacement, supportsHtmlInCanvas } from './displacement-engine.js'
import { createSvgDisplacement } from './svg-displacement.js'

const props = defineProps({
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260924: 'filter' (default) distorts the live DOM through an SVG filter
  // and runs in every modern browser; 'canvas' and 'auto' keep the experimental HTML-in-Canvas path opt-in for
  // browsers that actually expose it. Resolved once during setup - see `mode` below.
  engine:     { type: String, default: 'filter' },
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

const rootEl = ref(null)
const sourceEl = ref(null)
const contentEl = ref(null)
const outputEl = ref(null)

const canvasAvailable = supportsHtmlInCanvas()
const wantsCanvas = props.engine === 'canvas' || props.engine === 'auto'
// The engine is chosen once, during setup, so the slotted login form is never remounted by a branch flip.
const mode = ref(wantsCanvas && canvasAvailable ? 'canvas' : 'filter')

let instance = null
let disposed = false

const boot = () => {
  if (!contentEl.value) return
  if (mode.value === 'filter') {
    instance = createSvgDisplacement({ content: contentEl.value, container: rootEl.value }, props)
    return
  }
  if (sourceEl.value && outputEl.value) {
    instance = createDisplacement(
      { source: sourceEl.value, content: contentEl.value, output: outputEl.value },
      props
    )
  }
}

onMounted(async () => {
  await nextTick()
  if (disposed) return
  boot()
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260924: a browser can advertise HTML-in-Canvas and still fail to give us
  // a WebGL2 context; degrade to the SVG filter engine instead of dropping the effect entirely
  if (mode.value === 'canvas' && !instance) {
    mode.value = 'filter'
    await nextTick()
    if (disposed) return
    boot()
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
  <div ref="rootEl" class="canvasui-displacement" style="position: relative">
    <template v-if="mode === 'canvas'">
      <canvas
        ref="sourceEl"
        layoutsubtree="true"
        content="drawable"
        style="position: absolute; inset: 0; width: 100%; height: 100%"
      >
        <!-- overflow: visible, not auto: a scroll container could add a scrollbar that changes the content width, feeding back into the caller's measured height -->
        <div
          ref="contentEl"
          drawable
          style="position: relative; width: 100%; height: 100%; overflow: visible"
        >
          <slot />
        </div>
      </canvas>
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
    </template>
    <div
      v-else
      ref="contentEl"
      style="position: relative; width: 100%; height: 100%; overflow: visible"
    >
      <slot />
    </div>
  </div>
</template>
