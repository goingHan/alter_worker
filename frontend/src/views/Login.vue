<template>
  <div class="login-page">
    <canvas ref="canvas" class="bg-canvas"></canvas>
    <div class="aurora"></div>

    <Displacement
      class="login-card-wrap"
      :style="wrapStyle"
      engine="filter"
      :grid="24"
      :cell-aspect="1"
      :radius="0.14"
      :strength="0.12"
      :threshold="900"
      :relaxation="0.9"
      :shift="1.1"
      :aberration="1.6"
      :grain="0.08"
      :grain-size="1"
      :grain-speed="1"
      :scramble="0.8"
    >
      <div ref="cardRef" class="login-card">
      <div class="brand">
        <div class="brand-mark">
          <span class="brand-ring"></span>
          <Icon type="ios-pulse" size="26" class="brand-icon" />
        </div>
        <h1 class="brand-name">AlertWorker</h1>
        <p class="brand-slogan">于幽绿长夜，守望每一次告警</p>
      </div>

      <Form ref="formRef" :model="form" :rules="rules" @submit.native.prevent>
        <FormItem prop="username">
          <Input v-model="form.username" size="large" placeholder="用户名" prefix="ios-person-outline" @on-enter="handleLogin" />
        </FormItem>
        <FormItem prop="password">
          <Input v-model="form.password" type="password" size="large" placeholder="密码" prefix="ios-lock-outline" @on-enter="handleLogin" />
        </FormItem>
        <Button type="primary" size="large" long :loading="loading" @click="handleLogin">
          进入守望台
        </Button>
      </Form>
      </div>
    </Displacement>

    <p class="footer-line">AlertWorker · 守望每一次告警，静候每一个黎明</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import axios from 'axios'
import { Message } from 'view-ui-plus'
import { gsap } from 'gsap'
import { setAuth } from '../utils/auth'
import Displacement from '../components/canvasui/Displacement.vue'

const router = useRouter()
const route = useRoute()
const canvas = ref(null)
const cardRef = ref(null)
const cardSize = ref({ w: 0, h: 0 })
let cardObserver = null

const wrapStyle = computed(() => ({
  width: cardSize.value.w ? cardSize.value.w + 'px' : undefined,
  height: cardSize.value.h ? Math.ceil(cardSize.value.h) + 'px' : undefined,
  opacity: ready.value ? 1 : 0,
  transform: ready.value ? 'translateY(0)' : 'translateY(24px)'
}))
const ready = ref(false)
const loading = ref(false)
const formRef = ref(null)

const form = ref({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = () => {
  formRef.value.validate(async valid => {
    if (!valid) return
    loading.value = true
    try {
      const res = await axios.post('/pw/alertworker/dm/api/v1/auth/login', {
        username: form.value.username,
        password: form.value.password
      })
      setAuth(res.data.token, res.data.username, res.data.expiresIn)
      Message.success({ content: '欢迎回来，守望者' })
      router.push(route.query.redirect || '/')
    } catch (e) {
      Message.error({ content: e.response?.data?.error || '登录失败' })
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  setTimeout(() => { ready.value = true }, 120)
  gsap.fromTo('.brand-mark', { scale: 0.7, opacity: 0 }, { scale: 1, opacity: 1, duration: 1.1, ease: 'power2.out' })
  gsap.fromTo('.brand-name', { y: 18, opacity: 0 }, { y: 0, opacity: 1, duration: 0.9, delay: 0.25 })
  gsap.fromTo('.brand-slogan', { opacity: 0 }, { opacity: 1, duration: 1.2, delay: 0.6 })
  gsap.to('.brand-ring', { rotate: 360, duration: 24, repeat: -1, ease: 'none' })
  initCanvas()
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260924: the canvas engine captures the card into an absolutely positioned
  // canvas, so the wrapper cannot size from flow and both engines share one measured box - measure and bind it
  const cardEl = cardRef.value
  if (cardEl) {
    const measure = () => {
      const rect = cardEl.getBoundingClientRect()
      cardSize.value = { w: rect.width, h: rect.height }
    }
    measure()
    cardObserver = new ResizeObserver(measure)
    cardObserver.observe(cardEl)
  }
})

let rafId = null
let stars = []
let fireflies = []
let shooters = []
let tufts = []
let width = 0
let height = 0
let time = 0
let reduceMotion = false
// one reference scale keeps every silhouette in proportion on any window size
let S = 1

const SILH = '#050b08'

const initCanvas = () => {
  const cv = canvas.value
  const ctx = cv.getContext('2d')
  reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  const seed = () => {
    stars = []
    fireflies = []
    shooters = []
    for (let i = 0; i < 150; i++) {
      stars.push({
        x: Math.random() * width,
        y: Math.random() * height * 0.72,
        r: Math.random() * 1.3 + 0.3,
        base: Math.random() * 0.5 + 0.25,
        speed: Math.random() * 0.008 + 0.002,
        phase: Math.random() * Math.PI * 2
      })
    }
    for (let i = 0; i < 24; i++) {
      fireflies.push({
        x: Math.random() * width,
        y: height * 0.4 + Math.random() * height * 0.55,
        vx: (Math.random() - 0.5) * 0.25,
        vy: (Math.random() - 0.5) * 0.2,
        r: Math.random() * 2 + 1,
        phase: Math.random() * Math.PI * 2,
        speed: Math.random() * 0.02 + 0.008
      })
    }
    // foreground grass — seeded last so the tufts sit in front of every silhouette
    for (let i = 0; i < 30; i++) {
      const blades = []
      const n = 3 + Math.floor(Math.random() * 3)
      for (let b = 0; b < n; b++) {
        blades.push({
          dx: (Math.random() - 0.5) * 26,
          len: 16 + Math.random() * 40,
          lean: (Math.random() - 0.5) * 0.6,
          phase: Math.random() * Math.PI * 2
        })
      }
      tufts.push({
        x: Math.random() * width,
        y: groundY() + 2 + Math.random() * (height - groundY() - 2),
        s: 0.55 + Math.random() * 0.9,
        alpha: 0.35 + Math.random() * 0.5,
        blades,
        phase: Math.random() * Math.PI * 2
      })
    }
  }

  const groundY = () => height * 0.85
  // gentle rolling horizon — the ground is a curve, not a hard line
  const hillY = (x) => groundY() + Math.sin((x / Math.max(width, 1)) * Math.PI * 1.7) * height * 0.012

  // ---------------------------------------------------------------- sky

  // A scratch canvas big enough for one moon face. The crescent is cut out here rather than on
  // the scene canvas, because destination-out would punch a hole through the night sky too.
  const moonBuf = document.createElement('canvas')

  const drawMoon = () => {
    // A crescent, not a disc: the full face is painted with its maria, then the dark limb is
    // erased for real, so the night sky shows through the bite instead of a flat sky-coloured
    // patch sitting on top of the background gradient.
    // Kept small and set slightly right of centre so the figure on the ground reads as looking
    // up at it rather than standing under a lamp.
    const mx = width * 0.885
    const my = height * 0.16
    const r = height * 0.044

    // soft halo hugging the lit limb, so the crescent still carries a glow
    const hx = mx - r * 0.45
    const hy = my - r * 0.12
    const halo = ctx.createRadialGradient(hx, hy, r * 0.6, hx, hy, r * 7.5)
    halo.addColorStop(0, 'rgba(214, 238, 216, 0.16)')
    halo.addColorStop(0.26, 'rgba(172, 216, 186, 0.075)')
    halo.addColorStop(0.62, 'rgba(140, 195, 168, 0.026)')
    halo.addColorStop(1, 'rgba(140, 195, 168, 0)')

    // slightly tilted crescent: disc minus an offset disc
    const CA = -0.42 // keeps the horns from lining up as a mechanical vertical pair
    const bx = Math.cos(CA) * r * 0.5
    const by = Math.sin(CA) * r * 0.5

    const pad = Math.ceil(r * 1.2)
    const w = pad * 2 + 2
    moonBuf.width = w
    moonBuf.height = w
    const m = moonBuf.getContext('2d')
    m.clearRect(0, 0, w, w)
    m.save()
    m.translate(pad, pad)

    // the lit face, brightest toward the outer limb
    const disc = m.createRadialGradient(-r * 0.5, -r * 0.45, r * 0.05, 0, 0, r)
    disc.addColorStop(0, '#f6fbf0')
    disc.addColorStop(0.5, '#e2f0da')
    disc.addColorStop(1, '#c2d9c0')
    m.fillStyle = disc
    m.beginPath()
    m.arc(0, 0, r, 0, Math.PI * 2)
    m.fill()

    // No maria: at this size on the page they read as smudges, and a clean crescent is the
    // stronger, quieter shape against the figure looking up at it.

    // soft terminator: the lit face fades toward the dark limb instead of stopping dead
    const term = m.createLinearGradient(-r * 0.2, 0, r * 0.55, 0)
    term.addColorStop(0, 'rgba(30, 62, 48, 0)')
    term.addColorStop(0.6, 'rgba(30, 62, 48, 0.3)')
    term.addColorStop(1, 'rgba(30, 62, 48, 0.75)')
    m.fillStyle = term
    m.beginPath()
    m.arc(0, 0, r, 0, Math.PI * 2)
    m.fill()
    m.restore()

    // erase the dark limb: the bite leaves real transparency, so the sky shows through
    m.save()
    m.globalCompositeOperation = 'destination-out'
    m.beginPath()
    m.arc(pad + bx, pad + by, r * 1.02, 0, Math.PI * 2)
    m.fill()
    m.restore()

    ctx.drawImage(moonBuf, mx - pad, my - pad)

    // halo last, so the glow adds light without filling the crescent's hollow
    ctx.fillStyle = halo
    ctx.beginPath()
    ctx.arc(hx, hy, r * 7.5, 0, Math.PI * 2)
    ctx.fill()

    return { x: mx, y: my, r }
  }

  const drawDistantHills = (moon) => {
    const back = ctx.createLinearGradient(0, groundY() - height * 0.05, 0, groundY())
    back.addColorStop(0, 'rgba(19, 44, 33, 0)')
    back.addColorStop(1, 'rgba(19, 44, 33, 0.85)')
    ctx.fillStyle = back
    ctx.beginPath()
    ctx.moveTo(0, groundY())
    for (let x = 0; x <= width; x += width / 40) {
      ctx.lineTo(x, groundY() - height * 0.02 - Math.sin(x / width * Math.PI * 1.3 + 0.6) * height * 0.022 - Math.sin(x / width * Math.PI * 3.1) * height * 0.008)
    }
    ctx.lineTo(width, groundY())
    ctx.closePath()
    ctx.fill()

    // a far pine ridge: small, uneven, then a mist wash so it stays behind everything
    const ridgeTiers = [[0.3, 0.24, 0.2], [0.6, 0.18, 0.18], [0.85, 0.11, 0.16]]
    let k = 0
    for (let x = width * 0.035; x < width * 0.99; x += width * (0.052 + (Math.sin(k * 3.1) * 0.5 + 0.5) * 0.05)) {
      const t = x / width
      const hs = (0.016 + (Math.sin(t * 21.3) * 0.5 + 0.5) * 0.026) * height
      const base = groundY() - height * 0.004 - Math.sin(t * Math.PI * 1.3 + 0.6) * height * 0.02
      drawPine(x, base, hs, `rgba(11, 29, 21, ${0.34 + (Math.sin(t * 7.7) * 0.5 + 0.5) * 0.14})`, ridgeTiers)
      k += 1
    }

    const wash = ctx.createLinearGradient(0, groundY() - height * 0.1, 0, groundY())
    wash.addColorStop(0, 'rgba(20, 50, 38, 0)')
    wash.addColorStop(1, 'rgba(20, 50, 38, 0.55)')
    ctx.fillStyle = wash
    ctx.fillRect(0, groundY() - height * 0.1, width, height * 0.1)
  }

  // --------------------------------------------------------------- trees

  // A pine reads from its tier rhythm: a wide skirt low down, tiers narrowing as they climb.
  // `tiers` is [heightFraction, halfWidthFraction, heightFraction] — overlapping neighbours are
  // what turn a stack of triangles into a real conifer.
  const drawPine = (x, baseY, h, color, tiers) => {
    ctx.fillStyle = color
    const trunkW = Math.max(1, h * 0.03)
    ctx.beginPath()
    ctx.moveTo(x - trunkW, baseY)
    ctx.lineTo(x - trunkW * 0.45, baseY - h * 0.5)
    ctx.lineTo(x + trunkW * 0.45, baseY - h * 0.5)
    ctx.lineTo(x + trunkW, baseY)
    ctx.closePath()
    ctx.fill()

    tiers.forEach(([fy, fw, fh], i) => {
      const cy = baseY - h * fy
      const w = h * fw
      const tw = h * fw * 0.78
      const th = h * fh
      const skew = h * 0.02 * Math.sin(i * 1.9 + x * 0.013)
      // a slight left/right bias per tier keeps the outline from looking stamped
      const bias = 1 + Math.sin(i * 2.7 + x * 0.021) * 0.08
      ctx.beginPath()
      ctx.moveTo(x + skew, cy - th)
      ctx.quadraticCurveTo(x + tw * 0.5 + skew, cy - th * 0.3, x + w * bias + skew, cy + th * 0.55)
      ctx.quadraticCurveTo(x + w * 0.5 * bias + skew, cy + th * 0.85, x + w * 0.18 + skew, cy + th * 0.92)
      ctx.lineTo(x - w * 0.18 + skew, cy + th * 0.92)
      ctx.quadraticCurveTo(x - w * 0.5 / bias + skew, cy + th * 0.85, x - w / bias + skew, cy + th * 0.55)
      ctx.quadraticCurveTo(x - tw * 0.5 + skew, cy - th * 0.3, x + skew, cy - th)
      ctx.closePath()
      ctx.fill()
    })
  }

  // the owl's perch, drawn over the trunk so the feet have something to grip
  const drawPerch = (bx, by, ex, ey, w) => {
    const cx = (bx + ex) * 0.5
    const cy = (by + ey) * 0.5 + Math.max(10, w * 1.6)
    ctx.strokeStyle = SILH
    ctx.lineCap = 'round'
    ctx.lineWidth = w
    ctx.beginPath()
    ctx.moveTo(bx, by)
    ctx.quadraticCurveTo(cx, cy, ex, ey)
    ctx.stroke()
    // a fork near the tip, so the owl sits in the crook of a real branch
    ctx.lineWidth = w * 0.42
    ctx.beginPath()
    ctx.moveTo(cx, cy - w * 0.1)
    ctx.lineTo(ex - (ex - cx) * 0.3, ey - w * 0.5)
    ctx.stroke()
  }

  // --------------------------------------------------------------- owl

  const drawOwl = (x, y, s, tilt) => {
    // a slow breath + the branch sway, so the bird is alive without flapping
    const breath = 1 + Math.sin(time * 1.1) * 0.018
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(tilt + Math.sin(time * 0.5) * 0.012)
    ctx.scale(s * breath, s / breath)
    ctx.fillStyle = SILH
    ctx.strokeStyle = SILH

    // feet gripping the branch
    ctx.lineWidth = 1.5
    ctx.lineCap = 'round'
    for (const fx of [-7, 6]) {
      ctx.beginPath()
      ctx.moveTo(fx - 4, -4)
      ctx.quadraticCurveTo(fx, -1.5, fx + 4, -3.4)
      ctx.stroke()
    }

    // tail feathers
    ctx.beginPath()
    ctx.moveTo(-6, -8)
    ctx.lineTo(4, -7)
    ctx.lineTo(1, -1.5)
    ctx.lineTo(-5, -2.5)
    ctx.closePath()
    ctx.fill()

    // body, chest forward, shoulder softening into the head
    ctx.beginPath()
    ctx.moveTo(-15, -9)
    ctx.bezierCurveTo(-22, -18, -20, -36, -12, -45)
    ctx.bezierCurveTo(-7, -50, 7, -50, 13, -44)
    ctx.bezierCurveTo(21, -36, 22, -18, 14, -9)
    ctx.bezierCurveTo(9, -4, -9, -4, -15, -9)
    ctx.closePath()
    ctx.fill()

    // folded wing sweeping down the back
    ctx.beginPath()
    ctx.moveTo(-11, -45)
    ctx.bezierCurveTo(-23, -36, -24, -19, -15, -10)
    ctx.lineTo(-2, -12)
    ctx.bezierCurveTo(-10, -22, -11, -34, -6, -44)
    ctx.closePath()
    ctx.fill()

    // ear tufts
    ctx.beginPath()
    ctx.moveTo(-13, -58)
    ctx.lineTo(-17, -74)
    ctx.lineTo(-3, -64)
    ctx.closePath()
    ctx.fill()
    ctx.beginPath()
    ctx.moveTo(9, -65)
    ctx.lineTo(16, -75)
    ctx.lineTo(17, -58)
    ctx.closePath()
    ctx.fill()

    // head
    ctx.beginPath()
    ctx.ellipse(0, -58, 17, 15.5, 0, 0, Math.PI * 2)
    ctx.fill()

    // facial disc — a hair lighter so the face separates from the crown
    ctx.fillStyle = 'rgba(10, 26, 19, 1)'
    ctx.beginPath()
    ctx.ellipse(0, -55, 12, 10, 0, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = SILH

    // beak
    ctx.beginPath()
    ctx.moveTo(0, -56)
    ctx.lineTo(3.2, -51)
    ctx.lineTo(-3.2, -51)
    ctx.closePath()
    ctx.fill()

    // eyes — the brand's one warm light source on the bird
    const blink = Math.abs(Math.sin(time * 0.33)) > 0.05 ? 1 : 0.08
    const look = Math.sin(time * 0.22) * 1.6
    for (const ex of [-7, 7]) {
      ctx.fillStyle = 'rgba(150, 205, 132, 0.26)'
      ctx.beginPath()
      ctx.arc(ex + look, -58.5, 5.6, 0, Math.PI * 2)
      ctx.fill()
      ctx.fillStyle = '#c6e69c'
      ctx.beginPath()
      ctx.ellipse(ex + look, -58.5, 3.4, 3.4 * blink, 0, 0, Math.PI * 2)
      ctx.fill()
      ctx.fillStyle = '#14261a'
      ctx.beginPath()
      ctx.ellipse(ex + look * 1.2, -58.5, 1.5, 1.5 * blink, 0, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.restore()
  }

  // --------------------------------------------------------------- fox

  // A person and a fox share the open band to the right of the centred login card, facing each
  // other. Both are drawn from their own model space; the callers below decide the band, the
  // unit size and the anchors, so the pair stays clear of the card on any window width.
  // A figure and a fox, both seen from BEHIND, walking away from the viewer into the night.
  // They are deliberately small: the distance is the point, and the torch the figure carries is
  // the only warm light in the scene. Neither is ever drawn facing the camera, so no face is
  // needed and the whole read is silhouette.

  // ---- the figure, from behind, torch arm raised
  const drawBearer = (gx, gy, u) => {
    // Model space: origin between the heels on the ground, +x right, +y down, 1 unit = u px.
    // Heels (0,0) -> hips (0,-28) -> shoulders (0,-52) -> neck (0,-55.5) -> head centre (0,-60)
    // radius 6. The torso is one continuous shape reaching down past the hips, and the legs
    // start above that line, so no gap can open at the hips. The raised arm holds the torch out
    // beside the head, never crossing it. Returns the flame's base point.
    const mx = (v) => gx + v * u
    const my = (v) => gy + v * u

    ctx.save()
    ctx.fillStyle = SILH
    ctx.strokeStyle = SILH
    ctx.lineJoin = 'round'
    ctx.lineCap = 'round'

    // legs, seen from behind: straight, close together, tapering toward the ankles
    // legs, seen from behind: they start well inside the torso so the two shapes always overlap
    ctx.lineWidth = 3.6 * u
    ctx.beginPath()
    ctx.moveTo(mx(-3.4), my(-30))
    ctx.quadraticCurveTo(mx(-3.5), my(-14), mx(-3.3), my(-1.2))
    ctx.stroke()
    ctx.lineWidth = 3.4 * u
    ctx.beginPath()
    ctx.moveTo(mx(3.3), my(-30))
    ctx.quadraticCurveTo(mx(3.4), my(-14), mx(3.4), my(-1.2))
    ctx.stroke()
    // feet, small wedges turning slightly out
    ctx.beginPath()
    ctx.ellipse(mx(-3.6), my(-1), 2.7 * u, 1.2 * u, 0.06, 0, Math.PI * 2)
    ctx.fill()
    ctx.beginPath()
    ctx.ellipse(mx(3.7), my(-1), 2.7 * u, 1.2 * u, -0.06, 0, Math.PI * 2)
    ctx.fill()

    // torso: one continuous shape from the hips to the shoulders
    ctx.beginPath()
    ctx.moveTo(mx(-6.8), my(-25))
    ctx.quadraticCurveTo(mx(-8.2), my(-32), mx(-8.2), my(-41))
    ctx.bezierCurveTo(mx(-8.2), my(-48), mx(-6.8), my(-52), mx(-4), my(-52.8))
    ctx.quadraticCurveTo(mx(0), my(-54), mx(4), my(-52.8))
    ctx.bezierCurveTo(mx(6.8), my(-52), mx(8.2), my(-48), mx(8.2), my(-41))
    ctx.quadraticCurveTo(mx(8.2), my(-32), mx(6.8), my(-25))
    ctx.quadraticCurveTo(mx(0), my(-22.6), mx(-6.8), my(-25))
    ctx.closePath()
    ctx.fill()

    // neck
    ctx.beginPath()
    ctx.moveTo(mx(-2.6), my(-54))
    ctx.lineTo(mx(2.6), my(-54))
    ctx.lineTo(mx(2.6), my(-56.6))
    ctx.lineTo(mx(-2.6), my(-56.6))
    ctx.closePath()
    ctx.fill()

    // head, then hair over the whole back of it with a fall to the nape
    const hx = mx(0)
    const hy = my(-60)
    ctx.beginPath()
    ctx.ellipse(hx, hy, 5.2 * u, 5.6 * u, 0, 0, Math.PI * 2)
    ctx.fill()
    ctx.beginPath()
    ctx.moveTo(hx - 6 * u, hy + 0.6 * u)
    ctx.quadraticCurveTo(hx - 6.6 * u, hy - 5.4 * u, hx - 1.4 * u, hy - 6.4 * u)
    ctx.quadraticCurveTo(hx + 4.6 * u, hy - 6.4 * u, hx + 5.8 * u, hy - 1 * u)
    ctx.quadraticCurveTo(hx + 5.4 * u, hy + 4.6 * u, hx + 3 * u, hy + 6.4 * u)
    ctx.quadraticCurveTo(hx, hy + 4.6 * u, hx - 3.2 * u, hy + 5.8 * u)
    ctx.quadraticCurveTo(hx - 6 * u, hy + 4 * u, hx - 6 * u, hy + 0.6 * u)
    ctx.closePath()
    ctx.fill()

    // raised right arm, and the left arm hanging at the side
    ctx.lineWidth = 2.8 * u
    ctx.beginPath()
    ctx.moveTo(mx(5.4), my(-50.4))
    ctx.quadraticCurveTo(mx(8), my(-52), mx(8.4), my(-55.4))
    ctx.stroke()
    ctx.lineWidth = 2.3 * u
    ctx.beginPath()
    ctx.moveTo(mx(8.4), my(-55.4))
    ctx.quadraticCurveTo(mx(8.6), my(-57.4), mx(8), my(-58.6))
    ctx.stroke()
    ctx.lineWidth = 2.4 * u
    ctx.beginPath()
    ctx.moveTo(mx(-5.6), my(-50))
    ctx.quadraticCurveTo(mx(-7.6), my(-42), mx(-7), my(-33.6))
    ctx.stroke()

    // ---- the torch: staff standing in the raised hand, leaning outward, well clear of the head
    const burn = my(-82)
    ctx.strokeStyle = '#1d150d'
    ctx.lineWidth = 2 * u
    ctx.beginPath()
    ctx.moveTo(mx(8.4), my(-55))
    ctx.lineTo(mx(9.6), my(-80))
    ctx.stroke()
    // a wrap of cloth just under the flame
    ctx.strokeStyle = '#241a10'
    ctx.lineWidth = 2.8 * u
    ctx.beginPath()
    ctx.moveTo(mx(9.3), my(-76.4))
    ctx.lineTo(mx(9.55), my(-80.4))
    ctx.stroke()

    ctx.restore()
    return { x: mx(9.7), y: burn }
  }

  // ---- the torch flame and the warm light it throws
  const drawTorch = (fx, fy, u) => {
    // two slightly detuned sines give an irregular flicker rather than a pulse
    const flick = Math.sin(time * 7.1) * 0.5 + Math.sin(time * 11.7 + 1.3) * 0.5
    // the flame leans away from the bearer instead of back across their head
    const sway = (1.4 + flick * 0.6) * u

    // halo falling across the shoulders and the ground beyond
    const gl = ctx.createRadialGradient(fx, fy - 1 * u, 0, fx, fy - 1 * u, 26 * u)
    gl.addColorStop(0, 'rgba(255, 188, 104, ' + (0.24 + flick * 0.025).toFixed(3) + ')')
    gl.addColorStop(0.3, 'rgba(238, 152, 74, 0.085)')
    gl.addColorStop(0.64, 'rgba(214, 134, 72, 0.028)')
    gl.addColorStop(1, 'rgba(214, 134, 72, 0)')
    ctx.fillStyle = gl
    ctx.beginPath()
    ctx.arc(fx, fy - 1 * u, 26 * u, 0, Math.PI * 2)
    ctx.fill()

    // the flame: outer teardrop leaning away from the bearer, inner tongue, hot core
    ctx.beginPath()
    ctx.moveTo(fx + sway * 1.7, fy - 9.4 * u)
    ctx.quadraticCurveTo(fx + 3.1 * u, fy - 2.6 * u, fx + 1.7 * u, fy + 0.6 * u)
    ctx.quadraticCurveTo(fx - 0.2 * u, fy + 1.9 * u, fx - 1.9 * u, fy + 0.5 * u)
    ctx.quadraticCurveTo(fx - 3.4 * u, fy - 2.8 * u, fx + sway * 1.7, fy - 9.4 * u)
    ctx.closePath()
    ctx.fillStyle = 'rgba(240, 176, 96, 0.95)'
    ctx.fill()
    ctx.beginPath()
    ctx.moveTo(fx + sway, fy - 5.4 * u)
    ctx.quadraticCurveTo(fx + 1.8 * u, fy - 1.6 * u, fx + 0.9 * u, fy + 0.5 * u)
    ctx.quadraticCurveTo(fx - 0.2 * u, fy + 1.4 * u, fx - 1.1 * u, fy + 0.5 * u)
    ctx.quadraticCurveTo(fx - 2 * u, fy - 1.7 * u, fx + sway, fy - 5.4 * u)
    ctx.closePath()
    ctx.fillStyle = '#ffdfa6'
    ctx.fill()
    ctx.beginPath()
    ctx.ellipse(fx + sway * 0.5, fy - 1.2 * u, 0.9 * u, 1.7 * u, 0.15, 0, Math.PI * 2)
    ctx.fillStyle = 'rgba(255, 251, 232, 0.95)'
    ctx.fill()
  }

  // ---- the fox, from behind, walking away alongside
  const drawFoxBehind = (gx, gy, u) => {
    // Model space: origin between the hind paws on the ground, +x right, +y down. Seen from
    // behind: a back tapering up to the shoulders, two sharp ears, the muzzle just showing past
    // the right cheek, and the brush hanging down with a slow sway.
    const mx = (v) => gx + v * u
    const my = (v) => gy + v * u

    ctx.save()
    ctx.fillStyle = SILH
    ctx.strokeStyle = SILH
    ctx.lineJoin = 'round'
    ctx.lineCap = 'round'

    // tail first, so the body covers its root
    const tl = []
    const tr = []
    const N = 12
    for (let i = 0; i <= N; i++) {
      const t = i / N
      const cx = 0.6 + t * 3.4 + Math.sin(t * 3.6 - time * 1.5) * 1.2 * t
      const cy = -8 + t * 22
      const half = 4.6 * Math.cos(((t - 0.28) / 0.72) * Math.PI * 0.5) + 0.3
      const j = i % 2 === 0 ? 1.05 : 0.95
      tl.push([mx(cx - half * j), my(cy)])
      tr.push([mx(cx + half * j), my(cy)])
    }
    ctx.beginPath()
    tl.forEach((p, i) => (i ? ctx.lineTo(p[0], p[1]) : ctx.moveTo(p[0], p[1])))
    for (let i = tr.length - 1; i >= 0; i--) ctx.lineTo(tr[i][0], tr[i][1])
    ctx.closePath()
    ctx.fill()

    // hind legs and paws
    ctx.lineWidth = 3.4 * u
    ctx.beginPath()
    ctx.moveTo(mx(-4.6), my(-12))
    ctx.quadraticCurveTo(mx(-5.4), my(-6), mx(-4.8), my(-0.9))
    ctx.stroke()
    ctx.lineWidth = 3.2 * u
    ctx.beginPath()
    ctx.moveTo(mx(4.4), my(-12))
    ctx.quadraticCurveTo(mx(5), my(-6), mx(4.6), my(-0.9))
    ctx.stroke()

    // body, tapering up to the shoulders
    ctx.beginPath()
    ctx.moveTo(mx(-8.4), my(-11.4))
    ctx.bezierCurveTo(mx(-9.6), my(-19), mx(-8.6), my(-25), mx(-5.4), my(-28.6))
    ctx.quadraticCurveTo(mx(-1.4), my(-31), mx(2.6), my(-29.4))
    ctx.bezierCurveTo(mx(7), my(-27), mx(8.8), my(-20), mx(7.8), my(-12.4))
    ctx.quadraticCurveTo(mx(0), my(-9.4), mx(-8.4), my(-11.4))
    ctx.closePath()
    ctx.fill()

    // head, muzzle just past the right cheek, and two sharp ears
    ctx.beginPath()
    ctx.ellipse(mx(-0.4), my(-33), 5.2 * u, 4.7 * u, 0, 0, Math.PI * 2)
    ctx.fill()
    ctx.beginPath()
    ctx.moveTo(mx(2.4), my(-34.4))
    ctx.quadraticCurveTo(mx(7.4), my(-32.6), mx(9.4), my(-30.4))
    ctx.quadraticCurveTo(mx(9.6), my(-29.2), mx(8.2), my(-29))
    ctx.quadraticCurveTo(mx(5), my(-28.8), mx(2.2), my(-29.6))
    ctx.closePath()
    ctx.fill()
    ctx.beginPath()
    ctx.moveTo(mx(-5.4), my(-35))
    ctx.lineTo(mx(-6.8), my(-43.4))
    ctx.lineTo(mx(-1.6), my(-37))
    ctx.closePath()
    ctx.fill()
    ctx.beginPath()
    ctx.moveTo(mx(1.4), my(-37))
    ctx.lineTo(mx(5.4), my(-43.6))
    ctx.lineTo(mx(5.8), my(-35.6))
    ctx.closePath()
    ctx.fill()

    ctx.restore()
  }

  // Places the pair in the open band right of the centred login card: small, far away, seen from
  // behind, the figure carrying a torch. Sized from the window height so they stay distant, with
  // a band guard so a narrow window cannot push them under the card. The torch is painted after
  // both bodies, so its warm light falls across their shoulders.
  const drawCompanions = () => {
    // The login card is 400px wide and centred, so the open band begins at half its width plus
    // a gap. Sizing from that (rather than a fraction of the viewport) is what keeps the pair
    // out from behind the card on wide windows while still shrinking on narrow ones.
    const CARD_HALF = 208
    const bandLeft = width / 2 + CARD_HALF
    const band = Math.max(120, width - 24 - bandLeft)
    const u = Math.max(0.55, Math.min(height * 0.00195, band / 34))

    const px = bandLeft + 15 * u
    const py = hillY(px)
    const fx = px + 21 * u
    const fy = hillY(fx)

    const flame = drawBearer(px, py, u)
    drawFoxBehind(fx, fy, u)
    drawTorch(flame.x, flame.y, u)
  }


  const drawGround = () => {
    ctx.fillStyle = '#08140f'
    ctx.beginPath()
    ctx.moveTo(0, height)
    ctx.lineTo(0, hillY(0))
    for (let x = 0; x <= width; x += width / 60) ctx.lineTo(x, hillY(x))
    ctx.lineTo(width, height)
    ctx.closePath()
    ctx.fill()

    ctx.fillStyle = 'rgba(150, 220, 180, 0.10)'
    ctx.beginPath()
    ctx.moveTo(0, hillY(0))
    for (let x = 0; x <= width; x += width / 60) ctx.lineTo(x, hillY(x))
    ctx.strokeStyle = 'rgba(150, 220, 180, 0.10)'
    ctx.lineWidth = 1
    ctx.stroke()
  }

  // ------------------------------------------------- far settlement

  // A small village of tiny lit houses out along the horizon - fifteen buildings, a fraction of
  // the figure's height, washed with soft haze so it reads as very far away: somebody else's warm
  // windows while the figure on the ground carries their own light alone. Painted after the ground
  // and before the foreground grass, so the near grass blades pass in front of them.
  //
  // The windows come on and go out on their own. Each house owns a phase and a period drawn from
  // a fixed pseudo-random sequence, so the lights change one at a time at irregular intervals -
  // never all together, and the same way on every reload.
  //
  // LIGHT_TICK throttles every one of those changes: the whole village is only re-evaluated a few
  // times a minute, so a window opens or closes and then holds for seconds rather than flickering.
  const LIGHT_TICK = 2.4

  const drawFarHouses = () => {
    const rnd = (i, k) => {
      const v = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453
      return v - Math.floor(v)
    }

    // two clusters, one either side of the login card; six to the left, nine (with the church)
    // to the right, so the village reads around the card without crowding it
    const bands = [[0.372, 0.615, 6], [0.652, 0.995, 9]]
    const plan = []
    let seed = 0
    bands.forEach(([x0, x1, count], bandIndex) => {
      for (let n = 0; n < count; n++) {
        // deterministic jitter keeps the row irregular while the count stays exact
        const x = x0 + ((n + 0.5) / count) * (x1 - x0) + (rnd(seed, 21) - 0.5) * 0.022
        plan.push({
          x,
          w: (7 + rnd(seed, 2) * 11) * S,
          h: (6 + rnd(seed, 3) * 7) * S,
          back: rnd(seed, 1) > 0.48,
          i: seed,
          // the tallest building on the right becomes the church, the village's centre
          church: bandIndex === 1 && n === 3
        })
        seed += 1
      }
    })

    const bodyCol = '#0a1a13'
    ctx.save()
    ctx.globalAlpha = 0.87

    for (const b of plan) {
      const { i } = b
      const x = width * b.x
      // the back row sits a few pixels higher, which is all the perspective this needs
      const gy = hillY(x) - (b.back ? 5.6 : 0.4)
      const w = b.church ? 11 * S : b.w
      const h = b.church ? 16 * S : b.h
      const roof = h * (0.4 + rnd(i, 11) * 0.34)
      const dir = rnd(i, 12) > 0.5 ? 1 : -1

      ctx.fillStyle = bodyCol
      if (b.church) {
        // tower with a spire rather than a pitched roof
        const spire = h * 0.8
        ctx.beginPath()
        ctx.rect(x - w / 2, gy - h, w, h)
        ctx.fill()
        ctx.beginPath()
        ctx.moveTo(x - w * 0.58, gy - h)
        ctx.lineTo(x, gy - h - spire)
        ctx.lineTo(x + w * 0.58, gy - h)
        ctx.closePath()
        ctx.fill()
      } else {
        ctx.beginPath()
        ctx.rect(x - w / 2, gy - h, w, h)
        ctx.fill()
        // pitched roof, overhanging a little and not always centred
        const eave = 0.55 + rnd(i, 13) * 0.16
        ctx.beginPath()
        ctx.moveTo(x - w * eave, gy - h)
        ctx.lineTo(x + w * (rnd(i, 14) - 0.5) * 0.2, gy - h - roof)
        ctx.lineTo(x + w * eave, gy - h)
        ctx.closePath()
        ctx.fill()
        if (rnd(i, 15) > 0.5) {
          ctx.fillRect(x + w * 0.2, gy - h - roof * 0.7, Math.max(1, w * 0.09), roof * 0.76)
        }
      }

      // ---- this house's window schedule. The state is only re-evaluated on a slow tick, not
      // every frame, so a light can never chatter on and off: it holds for at least TICK seconds
      // per update. Each house still owns its own phase and period (9-17s, ~50% duty), so they
      // flip one at a time at irregular intervals and never together.
      const phase = rnd(i, 31) * Math.PI * 2
      const period = 9 + rnd(i, 32) * 8
      const lit = Math.sin((Math.floor(time / LIGHT_TICK) * LIGHT_TICK / period) * Math.PI * 2 + phase) > 0
      if (!lit) continue

      // ---- lit windows: the only warm points at this distance
      ctx.fillStyle = 'rgba(255, 199, 118, 0.95)'
      const wsz = Math.max(1.4, w * 0.2)
      const wx = x + dir * w * 0.17
      const wy = gy - h * 0.58
      if (b.church) {
        ctx.fillRect(wx - wsz / 2, gy - h * 0.74, wsz, wsz * 1.5)
        ctx.fillRect(wx - wsz / 2, gy - h * 0.34, wsz, wsz * 1.1)
      } else {
        ctx.fillRect(wx - wsz / 2, wy - wsz / 2, wsz, wsz)
        if (h > 9 * S && rnd(i, 17) > 0.5) ctx.fillRect(wx - wsz / 2, wy - wsz * 1.65, wsz, wsz * 0.8)
      }

      const rad = wsz * (b.church ? 6 : 4.6)
      const gy2 = b.church ? gy - h * 0.55 : wy
      const glow = ctx.createRadialGradient(wx, gy2, 0, wx, gy2, rad)
      glow.addColorStop(0, 'rgba(255, 190, 110, 0.3)')
      glow.addColorStop(0.35, 'rgba(240, 160, 90, 0.1)')
      glow.addColorStop(0.72, 'rgba(220, 145, 85, 0.03)')
      glow.addColorStop(1, 'rgba(220, 145, 85, 0)')
      ctx.fillStyle = glow
      ctx.beginPath()
      ctx.arc(wx, gy2, rad, 0, Math.PI * 2)
      ctx.fill()
    }

    ctx.restore()

    // a soft radial wash over the settlement, so it sits behind the night rather than on it.
    // Deliberately not a fillRect: a rectangular wash leaves visible straight seams in the sky.
    const hx = width * 0.74
    const hy = groundY()
    const hrad = width * 0.38
    const haze = ctx.createRadialGradient(hx, hy, hrad * 0.1, hx, hy, hrad)
    haze.addColorStop(0, 'rgba(24, 58, 44, 0.32)')
    haze.addColorStop(0.48, 'rgba(24, 58, 44, 0.15)')
    haze.addColorStop(1, 'rgba(24, 58, 44, 0)')
    ctx.fillStyle = haze
    ctx.beginPath()
    ctx.arc(hx, hy, hrad, 0, Math.PI * 2)
    ctx.fill()
  }

  const drawGrass = () => {
    ctx.lineCap = 'round'
    for (const t of tufts) {
      ctx.globalAlpha = t.alpha
      ctx.strokeStyle = '#0a1811'
      for (const b of t.blades) {
        const sway = b.lean + Math.sin(time * 0.9 + b.phase + t.phase) * 0.1
        ctx.lineWidth = 1.6 * t.s
        ctx.beginPath()
        ctx.moveTo(t.x + b.dx * t.s, t.y)
        ctx.quadraticCurveTo(
          t.x + b.dx * t.s + sway * b.len * t.s * 0.4,
          t.y - b.len * t.s * 0.6,
          t.x + b.dx * t.s + sway * b.len * t.s,
          t.y - b.len * t.s
        )
        ctx.stroke()
      }
    }
    ctx.globalAlpha = 1
  }

  const drawBackground = () => {
    const grad = ctx.createLinearGradient(0, 0, 0, height)
    grad.addColorStop(0, '#07120e')
    grad.addColorStop(0.55, '#0b1a13')
    grad.addColorStop(1, '#0e211a')
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, width, height)
  }

  const drawMist = () => {
    const bands = [
      { y: 0.18, w: 0.5, speed: 0.4, phase: 0 },
      { y: 0.34, w: 0.7, speed: 0.28, phase: 2.1 },
      { y: 0.56, w: 0.6, speed: 0.35, phase: 4.2 }
    ]
    for (const b of bands) {
      const drift = Math.sin(time * b.speed + b.phase) * width * 0.1
      const cx = width * 0.5 + drift
      const cy = height * b.y
      const rx = width * b.w
      const ry = height * 0.035
      const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, rx)
      grad.addColorStop(0, 'rgba(120, 175, 148, 0.06)')
      grad.addColorStop(1, 'rgba(120, 175, 148, 0)')
      ctx.fillStyle = grad
      ctx.beginPath()
      ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  const drawStars = () => {
    for (const s of stars) {
      const twinkle = s.base + Math.sin(s.phase) * 0.35
      ctx.globalAlpha = Math.max(0.05, twinkle)
      ctx.fillStyle = '#b9dcc4'
      ctx.beginPath()
      ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2)
      ctx.fill()
      s.phase += s.speed
    }
    ctx.globalAlpha = 1
  }

  const drawMainTree = () => {
    const tx = width * 0.15
    const base = groundY() + 12
    const h = height * 0.44
    const baseW = h * 0.075
    const tipX = tx + h * 0.03

    // limbs off the trunk, drooping out and down; the short bare twigs break the cone outline
    const limbs = [
      [-1, 0.16, 0.24, 0.13],
      [1, 0.26, 0.26, 0.15],
      [-1, 0.48, 0.2, 0.11],
      [1, 0.66, 0.16, 0.1]
    ]
    for (const [dir, hy, len, w] of limbs) {
      const startY = base - h * (0.26 + hy * 0.66)
      const bx = tx + dir * baseW * 0.3
      const ex = bx + dir * h * len
      const ey = startY + h * 0.1
      ctx.strokeStyle = SILH
      ctx.lineCap = 'round'
      ctx.lineWidth = Math.max(1.4, h * w * 0.5)
      ctx.beginPath()
      ctx.moveTo(bx, startY)
      ctx.quadraticCurveTo((bx + ex) * 0.5, (startY + ey) * 0.5 - h * 0.025, ex, ey)
      ctx.stroke()
    }

    // trunk
    ctx.strokeStyle = SILH
    ctx.lineCap = 'round'
    for (const [w, k] of [[0.9, 1], [0.45, 1.05]]) {
      ctx.lineWidth = Math.max(1.6, baseW * w)
      ctx.beginPath()
      ctx.moveTo(tx - baseW * 0.5 * k, base)
      ctx.bezierCurveTo(tx - baseW * 0.34, base - h * 0.32, tipX - baseW * 0.34, base - h * 0.66, tipX, base - h)
      ctx.stroke()
    }

    // canopy: the skirt flares low and the tiers tighten as they climb, so the owl's perch can
    // leave the trunk below the skirt and still read against the sky
    const treeTiers = [
      [0.1, 0.3, 0.15], [0.22, 0.275, 0.13], [0.34, 0.25, 0.12], [0.46, 0.225, 0.11],
      [0.58, 0.195, 0.1], [0.7, 0.165, 0.095], [0.81, 0.13, 0.09], [0.91, 0.09, 0.085]
    ]
    drawPine(tx, base, h, SILH, treeTiers)

    // the perch the owl grips — a limb that clears the skirt on the right
    const bx = tx + baseW * 0.3
    const by = base - h * 0.34
    const ex = bx + h * 0.3
    const ey = by + h * 0.07
    drawPerch(bx, by, ex, ey, Math.max(2.5, h * 0.02))
    return { x: ex - h * 0.05, y: ey - h * 0.002, tilt: Math.atan2(ey - by, ex - bx) - 0.04 }
  }

  const drawFireflies = () => {
    for (const f of fireflies) {
      f.x += f.vx
      f.y += f.vy
      if (f.x < 0 || f.x > width) f.vx *= -1
      if (f.y < height * 0.3 || f.y > height * 0.97) f.vy *= -1
      const glow = 0.35 + Math.sin(f.phase) * 0.3
      f.phase += f.speed
      const rg = ctx.createRadialGradient(f.x, f.y, 0, f.x, f.y, 26)
      rg.addColorStop(0, `rgba(140, 220, 170, ${Math.max(0, glow)})`)
      rg.addColorStop(0.35, `rgba(110, 190, 145, ${Math.max(0, glow * 0.4)})`)
      rg.addColorStop(1, 'rgba(110, 190, 145, 0)')
      ctx.fillStyle = rg
      ctx.beginPath()
      ctx.arc(f.x, f.y, 26, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  const drawShooters = () => {
    if (!reduceMotion && Math.random() < 0.0035) {
      shooters.push({
        x: Math.random() * width,
        y: Math.random() * height * 0.3,
        vx: (Math.random() * 4 + 3) * (Math.random() > 0.5 ? 1 : -1),
        vy: Math.random() * 1.6 + 1,
        life: 1
      })
    }
    shooters = shooters.filter(sh => sh.life > 0)
    for (const sh of shooters) {
      sh.x += sh.vx
      sh.y += sh.vy
      sh.life -= 0.02
      const trail = ctx.createLinearGradient(sh.x, sh.y, sh.x - sh.vx * 9, sh.y - sh.vy * 9)
      trail.addColorStop(0, `rgba(190, 235, 205, ${sh.life})`)
      trail.addColorStop(1, 'rgba(190, 235, 205, 0)')
      ctx.strokeStyle = trail
      ctx.lineWidth = 1.6
      ctx.beginPath()
      ctx.moveTo(sh.x, sh.y)
      ctx.lineTo(sh.x - sh.vx * 9, sh.y - sh.vy * 9)
      ctx.stroke()
    }
  }

  const resize = () => {
    width = cv.width = window.innerWidth
    height = cv.height = window.innerHeight
    S = Math.min(1.7, Math.max(0.68, height / 900))
    seed()
  }
  window.addEventListener('resize', resize)
  resize()

  const draw = () => {
    ctx.clearRect(0, 0, width, height)
    drawBackground()
    drawMist()
    drawStars()
    const moon = drawMoon()
    drawDistantHills(moon)
    drawFireflies()
    drawShooters()
    drawGround()
    drawFarHouses()
    const perch = drawMainTree()
    drawOwl(perch.x, perch.y, 1.3 * S, perch.tilt)
    drawCompanions()
    drawGrass()
    if (!reduceMotion) {
      time += 0.016
      rafId = requestAnimationFrame(draw)
    }
  }
  draw()
}

onUnmounted(() => {
  if (rafId) cancelAnimationFrame(rafId)
  if (cardObserver) cardObserver.disconnect()
})
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #07120e;
}

.bg-canvas {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.aurora {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background:
    radial-gradient(52% 34% at 18% 16%, rgba(64, 148, 100, 0.13), transparent 70%),
    radial-gradient(44% 30% at 82% 72%, rgba(56, 130, 160, 0.10), transparent 70%),
    radial-gradient(60% 46% at 50% 50%, rgba(38, 90, 66, 0.08), transparent 75%);
  animation: breathe 11s ease-in-out infinite;
}

@keyframes breathe {
  0%, 100% { opacity: 0.75; }
  50% { opacity: 1; }
}

.login-card-wrap {
  position: relative;
  z-index: 3;
  box-sizing: border-box;
  width: 400px;
  max-width: calc(100vw - 48px);
  border-radius: 18px;
  background: rgba(17, 34, 26, 0.72);
  /* 1px border -> inset ring: a border would sit between the wrapper's border box and the
     captured canvas (inset: 0 resolves to the padding box) and clip the captured card by 1px. */
  box-shadow: 0 18px 60px rgba(0, 0, 0, 0.5), inset 0 0 0 1px rgba(110, 175, 138, 0.22), inset 0 1px 0 rgba(170, 220, 190, 0.08);
  backdrop-filter: blur(14px);
  transition: opacity 0.9s ease, transform 0.9s ease;
}

.login-card {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  padding: 44px 40px 34px;
  background: transparent;
  border: none;
  border-radius: 0;
  box-shadow: none;
  backdrop-filter: none;
}

.brand {
  text-align: center;
  margin-bottom: 30px;
}

.brand-mark {
  position: relative;
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 42%, rgba(140, 215, 170, 0.22), rgba(64, 130, 94, 0.08) 68%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-ring {
  position: absolute;
  inset: -7px;
  border-radius: 50%;
  border: 1px dashed rgba(130, 200, 160, 0.4);
}

.brand-icon {
  color: #8fd0a8;
  text-shadow: 0 0 18px rgba(140, 215, 170, 0.7);
}

.brand-name {
  font-size: 26px;
  letter-spacing: 3px;
  color: #d9ecdf;
  margin: 0 0 8px;
  font-weight: 600;
}

.brand-slogan {
  font-size: 13px;
  color: #7fa48f;
  letter-spacing: 2px;
  margin: 0;
}

.login-hint {
  margin-top: 18px;
  text-align: center;
  font-size: 12px;
  color: rgba(140, 170, 152, 0.6);
}

.footer-line {
  position: absolute;
  bottom: 26px;
  width: 100%;
  text-align: center;
  z-index: 3;
  font-size: 12px;
  letter-spacing: 1.5px;
  color: rgba(130, 165, 145, 0.45);
}

:deep(.ivu-input-large) {
  height: 44px;
  background: rgba(9, 22, 16, 0.75);
  border-color: rgba(110, 175, 138, 0.25);
  color: #d9ecdf;
}
:deep(.ivu-input-large:focus) { border-color: #4e8a6a; }
:deep(.ivu-input-prefix) { color: #6e9c81; }

:deep(.ivu-btn-primary.ivu-btn-large) {
  height: 44px;
  background: linear-gradient(90deg, #3f7d5d, #549a75);
  border: none;
  letter-spacing: 4px;
  box-shadow: 0 6px 22px rgba(64, 140, 98, 0.35);
}
</style>
