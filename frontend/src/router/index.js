import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'

const routes = [
  { path: '/', component: Dashboard },
  { path: '/api-keys', component: () => import('../views/ApiKeys.vue') },
  { path: '/channels', component: () => import('../views/Channels.vue') },
  { path: '/assignees', component: () => import('../views/Assignees.vue') },
  { path: '/groups', component: () => import('../views/Groups.vue') },
  { path: '/templates', component: () => import('../views/Templates.vue') },
  { path: '/rules', component: () => import('../views/Rules.vue') },
  { path: '/history', component: () => import('../views/History.vue') },
  { path: '/failed', component: () => import('../views/FailedMessages.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
