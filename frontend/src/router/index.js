import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import Dashboard from '../views/Dashboard.vue'
import { isAuthenticated } from '../utils/auth'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', component: Dashboard },
      { path: 'history', component: () => import('../views/History.vue') },
      { path: 'rules', component: () => import('../views/Rules.vue') },
      { path: 'templates', component: () => import('../views/Templates.vue') },
      { path: 'groups', component: () => import('../views/Groups.vue') },
      { path: 'assignees', component: () => import('../views/Assignees.vue') },
      { path: 'channels', component: () => import('../views/Channels.vue') },
      { path: 'api-keys', component: () => import('../views/ApiKeys.vue') },
      { path: 'integration/prometheus', component: () => import('../views/Prometheus.vue') },
      { path: 'integration/notification', component: () => import('../views/NotificationGuide.vue') },
      { path: 'nodes', component: () => import('../views/Nodes.vue') },
      { path: 'test-message', component: () => import('../views/TestMessage.vue') },
      { path: 'reports', component: () => import('../views/Reports.vue') }
    ]
  }
]

const router = createRouter({
  // 以 vite base（前端上下文路径 /pw/alertworker/ui/）为历史基址，路由跳转全部 base-aware
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach((to) => {
  const authed = isAuthenticated()
  if (to.path !== '/login' && !authed) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && authed) {
    return { path: '/' }
  }
  return true
})

export default router
