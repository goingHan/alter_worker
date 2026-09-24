<template>
  <div class="login-page">
    <canvas ref="canvas" class="bg-canvas"></canvas>
    <div class="aurora"></div>

    <Displacement
      class="login-card-wrap"
      :style="wrapStyle"
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
  // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260924: the effect captures the card into an absolutely positioned canvas, so the wrapper cannot size from flow - measure and bind it
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
let width = 0
let height = 0
let time = 0
let reduceMotion = false

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
  }

  const groundY = () => height * 0.85

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

  const drawRidge = () => {
    // flat foreground ground — the figures stand exactly on this line
    ctx.fillStyle = '#08140f'
    ctx.fillRect(0, groundY(), width, height - groundY())
    // subtle horizon glow
    ctx.fillStyle = 'rgba(140, 215, 170, 0.08)'
    ctx.fillRect(0, groundY(), width, 1)
  }

  const drawLanternGlow = (x, y) => {
    const breathe = 0.8 + Math.sin(time * 1.2) * 0.2
    const r = height * 0.055 * breathe
    const grad = ctx.createRadialGradient(x, y, 0, x, y, r * 4)
    grad.addColorStop(0, `rgba(240, 200, 130, ${0.4 * breathe})`)
    grad.addColorStop(0.4, 'rgba(240, 200, 130, 0.12)')
    grad.addColorStop(1, 'rgba(240, 200, 130, 0)')
    ctx.fillStyle = grad
    ctx.beginPath()
    ctx.arc(x, y, r * 4, 0, Math.PI * 2)
    ctx.fill()
  }

  const drawWatchman = () => {
    const g = groundY()
    const wx = width * 0.68
    const h = height * 0.17

    ctx.save()
    ctx.translate(wx, g)
    ctx.fillStyle = SILH
    // cloak
    ctx.beginPath()
    ctx.moveTo(-h * 0.16, 0)
    ctx.lineTo(-h * 0.11, -h * 0.56)
    ctx.quadraticCurveTo(0, -h * 0.63, h * 0.11, -h * 0.56)
    ctx.lineTo(h * 0.16, 0)
    ctx.closePath()
    ctx.fill()
    // head + hood
    ctx.beginPath()
    ctx.arc(0, -h * 0.66, h * 0.088, 0, Math.PI * 2)
    ctx.fill()
    // arm raised toward the lantern
    ctx.strokeStyle = SILH
    ctx.lineWidth = h * 0.035
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(h * 0.07, -h * 0.5)
    ctx.lineTo(h * 0.3, -h * 0.78)
    ctx.stroke()
    // staff
    ctx.beginPath()
    ctx.moveTo(h * 0.3, -h * 0.78)
    ctx.lineTo(h * 0.3, -h * 1.14)
    ctx.stroke()
    // lantern body
    ctx.fillStyle = '#241a10'
    ctx.fillRect(h * 0.26, -h * 1.24, h * 0.08, h * 0.12)
    ctx.restore()

    drawLanternGlow(wx + h * 0.3, g - h * 1.18)
  }

  const drawFox = () => {
    const g = groundY()
    const fx = width * 0.58
    const s = height * 0.055
    const swish = Math.sin(time * 0.7) * 0.12

    ctx.fillStyle = SILH
    // sitting body — haunch and chest both rest on the ground
    ctx.beginPath()
    ctx.moveTo(fx - s * 0.55, g)
    ctx.quadraticCurveTo(fx - s * 0.72, g - s * 0.85, fx - s * 0.35, g - s * 1.1)
    ctx.quadraticCurveTo(fx - s * 0.05, g - s * 1.25, fx + s * 0.25, g - s * 1.05)
    ctx.lineTo(fx + s * 0.3, g)
    ctx.closePath()
    ctx.fill()
    // head with pointed snout
    ctx.beginPath()
    ctx.moveTo(fx - s * 0.05, g - s * 1.12)
    ctx.quadraticCurveTo(fx + s * 0.28, g - s * 1.32, fx + s * 0.72, g - s * 1.08)
    ctx.lineTo(fx + s * 1.05, g - s * 0.92)
    ctx.quadraticCurveTo(fx + s * 0.82, g - s * 0.8, fx + s * 0.48, g - s * 0.86)
    ctx.quadraticCurveTo(fx + s * 0.22, g - s * 0.92, fx - s * 0.05, g - s * 1.06)
    ctx.closePath()
    ctx.fill()
    // two large ears
    ctx.beginPath()
    ctx.moveTo(fx + s * 0.08, g - s * 1.22)
    ctx.lineTo(fx + s * 0.22, g - s * 1.72)
    ctx.lineTo(fx + s * 0.4, g - s * 1.2)
    ctx.closePath()
    ctx.fill()
    ctx.beginPath()
    ctx.moveTo(fx + s * 0.36, g - s * 1.2)
    ctx.lineTo(fx + s * 0.52, g - s * 1.68)
    ctx.lineTo(fx + s * 0.66, g - s * 1.18)
    ctx.closePath()
    ctx.fill()
    // bushy tail curling up behind
    ctx.beginPath()
    ctx.moveTo(fx - s * 0.5, g - s * 0.35)
    ctx.quadraticCurveTo(fx - s * 1.15, g - s * 0.5 + swish * s, fx - s * 1.18, g - s * 1.1)
    ctx.quadraticCurveTo(fx - s * 1.02, g - s * 1.5, fx - s * 0.62, g - s * 1.2)
    ctx.quadraticCurveTo(fx - s * 0.45, g - s * 1.0, fx - s * 0.5, g - s * 0.5)
    ctx.closePath()
    ctx.fill()
  }

  const drawOwl = () => {
    const ox = width * 0.16
    const oy = height * 0.26
    const s = height * 0.05
    const blink = Math.abs(Math.sin(time * 0.35)) > 0.04 ? 1 : 0.12

    ctx.strokeStyle = SILH
    ctx.lineWidth = s * 0.16
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(ox - s * 1.9, oy + s * 0.25)
    ctx.lineTo(ox + s * 1.9, oy - s * 0.12)
    ctx.stroke()
    // body
    ctx.fillStyle = SILH
    ctx.beginPath()
    ctx.ellipse(ox, oy - s * 0.5, s * 0.55, s * 0.7, 0, 0, Math.PI * 2)
    ctx.fill()
    // head
    ctx.beginPath()
    ctx.arc(ox, oy - s * 1.28, s * 0.5, 0, Math.PI * 2)
    ctx.fill()
    // ear tufts
    ctx.beginPath()
    ctx.moveTo(ox - s * 0.42, oy - s * 1.7)
    ctx.lineTo(ox - s * 0.24, oy - s * 1.95)
    ctx.lineTo(ox - s * 0.1, oy - s * 1.72)
    ctx.closePath()
    ctx.fill()
    ctx.beginPath()
    ctx.moveTo(ox + s * 0.1, oy - s * 1.72)
    ctx.lineTo(ox + s * 0.24, oy - s * 1.95)
    ctx.lineTo(ox + s * 0.42, oy - s * 1.7)
    ctx.closePath()
    ctx.fill()
    // blinking eyes
    ctx.fillStyle = '#c9e6a0'
    ctx.beginPath()
    ctx.ellipse(ox - s * 0.2, oy - s * 1.32, s * 0.12, s * 0.12 * blink, 0, 0, Math.PI * 2)
    ctx.fill()
    ctx.beginPath()
    ctx.ellipse(ox + s * 0.2, oy - s * 1.32, s * 0.12, s * 0.12 * blink, 0, 0, Math.PI * 2)
    ctx.fill()
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
    seed()
  }
  window.addEventListener('resize', resize)
  resize()

  const draw = () => {
    ctx.clearRect(0, 0, width, height)
    drawBackground()
    drawMist()
    drawStars()
    drawRidge()
    drawOwl()
    drawWatchman()
    drawFox()
    drawFireflies()
    drawShooters()
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
