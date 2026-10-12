<template>
  <div class="app">
    <Layout>
      <Sider collapsible :collapsed-width="78" v-model="isCollapsed" class="sider">
        <div class="logo" @click="$router.push('/')">
          <div class="logo-mark">
            <Icon type="ios-pulse" size="20" />
          </div>
          <transition name="fade">
            <span v-show="!isCollapsed" class="logo-text">AlertWorker</span>
          </transition>
        </div>
        <Menu theme="light" width="auto" :class="menuitemClasses"
              :active-name="activeMenu" :open-names="openNames" @on-open-change="onOpenChange">
          <MenuItem name="/" to="/">
            <Icon type="ios-speedometer-outline" />
            <span title="仪表盘">仪表盘</span>
          </MenuItem>
          <Submenu name="monitor">
            <template #title>
              <Icon type="ios-eye-outline" />
              <span title="告警监控">告警监控</span>
            </template>
            <MenuItem name="/history" to="/history">
              <Icon type="ios-time-outline" />
              <span title="告警历史">告警历史</span>
            </MenuItem>
          </Submenu>
          <Submenu name="access">
            <template #title>
              <Icon type="ios-keypad-outline" />
              <span title="接入管理">接入管理</span>
            </template>
            <MenuItem name="/api-keys" to="/api-keys">
              <Icon type="ios-key-outline" />
              <span title="API 密钥">API 密钥</span>
            </MenuItem>
          </Submenu>
          <Submenu name="notify">
            <template #title>
              <Icon type="ios-git-branch" />
              <span title="通知配置">通知配置</span>
            </template>
            <MenuItem name="/rules" to="/rules">
              <Icon type="ios-alert-outline" />
              <span title="告警规则">告警规则</span>
            </MenuItem>
            <MenuItem name="/channels" to="/channels">
              <Icon type="ios-mail-outline" />
              <span title="通知渠道">通知渠道</span>
            </MenuItem>
            <MenuItem name="/templates" to="/templates">
              <Icon type="ios-document-outline" />
              <span title="消息模板">消息模板</span>
            </MenuItem>
            <MenuItem name="/groups" to="/groups">
              <Icon type="ios-contacts-outline" />
              <span title="通信组">通信组</span>
            </MenuItem>
            <MenuItem name="/assignees" to="/assignees">
              <Icon type="ios-people-outline" />
              <span title="值班人员">值班人员</span>
            </MenuItem>
            <MenuItem name="/test-message" to="/test-message">
              <Icon type="ios-timer-outline" />
              <span title="周期测试消息">周期测试消息</span>
            </MenuItem>
            <MenuItem name="/reports" to="/reports">
              <Icon type="ios-paper-outline" />
              <span title="告警日报">告警日报</span>
            </MenuItem>
            <MenuItem name="/warning-digest" to="/warning-digest">
              <Icon type="ios-warning-outline" />
              <span title="告警汇总">告警汇总</span>
            </MenuItem>
          </Submenu>
          <Submenu name="integration">
            <template #title>
              <Icon type="ios-link-outline" />
              <span title="系统对接">系统对接</span>
            </template>
            <MenuItem name="/integration/prometheus" to="/integration/prometheus">
              <Icon type="ios-git-network" />
              <span title="Prometheus 对接">Prometheus 对接</span>
            </MenuItem>
            <MenuItem name="/integration/notification" to="/integration/notification">
              <Icon type="ios-notifications-outline" />
              <span title="通知对接">通知对接</span>
            </MenuItem>
          </Submenu>
          <Submenu name="system">
            <template #title>
              <Icon type="ios-cog-outline" />
              <span title="系统管理">系统管理</span>
            </template>
            <MenuItem name="/nodes" to="/nodes">
              <Icon type="ios-hammer-outline" />
              <span title="节点管理">节点管理</span>
            </MenuItem>
          </Submenu>
        </Menu>
      </Sider>
      <Layout class="main-layout">
        <Header class="header">
          <div class="header-title">{{ pageTitle }}</div>
          <div class="header-actions">
            <Dropdown trigger="click" placement="bottom-end" transfer @on-click="onThemeChange">
              <span class="icon-btn" :title="'主题：' + currentThemeName">
                <Icon type="ios-color-palette-outline" size="18" />
              </span>
              <template #list>
                <DropdownMenu>
                  <DropdownItem v-for="t in themes" :key="t.key" :name="t.key" :selected="t.key === currentThemeKey">
                    {{ t.name }} · {{ t.desc }}
                  </DropdownItem>
                </DropdownMenu>
              </template>
            </Dropdown>
            <span class="divider"></span>
            <Dropdown trigger="click" placement="bottom-end" transfer @on-click="onUserAction">
              <span class="user-trigger">
                <Avatar icon="ios-person" size="small" />
                <span class="user-name">{{ username }}</span>
                <Icon type="ios-arrow-down" size="12" class="user-arrow" />
              </span>
              <template #list>
                <DropdownMenu>
                  <DropdownItem name="logout">
                    <Icon type="ios-log-out-outline" /> 退出登录
                  </DropdownItem>
                </DropdownMenu>
              </template>
            </Dropdown>
          </div>
        </Header>
        <Content class="content">
          <router-view />
        </Content>
      </Layout>
    </Layout>
  </div>
</template>

<script setup>
// #AI-CODE-FILE-dev_release/v0.0.1-20260901: extract app shell into a layout so /login can render full-screen
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listThemes, applyTheme, getThemeKey } from '../theme'
import { clearAuth, getUsername, isAuthenticated } from '../utils/auth'
import request from '../api/request'

const route = useRoute()
const router = useRouter()

const isCollapsed = ref(false)
const openNames = ref(['monitor', 'notify', 'access', 'integration', 'system'])
const themes = listThemes()
const currentThemeKey = ref(getThemeKey())
const currentThemeName = computed(() => themes.find(t => t.key === currentThemeKey.value)?.name || '')
const username = ref(getUsername())

const menuitemClasses = computed(() => {
  return ['menu-item', isCollapsed.value ? 'collapsed-menu' : '']
})

const activeMenu = computed(() => {
  if (route.path === '/') return '/'
  return route.path
})

const pageTitles = {
  '/': '仪表盘',
  '/history': '告警历史',
  '/failed': '失败消息',
  '/rules': '告警规则',
  '/templates': '消息模板',
  '/groups': '通信组',
  '/assignees': '值班人员',
  '/channels': '通知渠道',
  '/api-keys': 'API 密钥',
  '/integration/prometheus': 'Prometheus 对接',
  '/integration/notification': '通知对接',
  '/nodes': '节点管理',
  '/test-message': '周期测试消息',
  '/reports': '告警日报',
  '/warning-digest': '告警汇总'
}

const pageTitle = computed(() => pageTitles[route.path] || 'AlertWorker')

const onOpenChange = (names) => {
  openNames.value = names
}

const onThemeChange = (key) => {
  if (!key) return
  currentThemeKey.value = key
  applyTheme(key)
}

const onUserAction = (name) => {
  if (name === 'logout') {
    clearAuth()
    router.push('/login')
  }
}

onMounted(() => {
  if (isAuthenticated()) {
    request.get('/auth/me').catch(() => {})
  }
})
</script>

<style scoped>
.app {
  min-height: 100vh;
  background: var(--aw-bg);
  transition: background var(--aw-transition);
}

.sider {
  background: var(--aw-sider);
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.15);
  transition: background var(--aw-transition);
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  border-bottom: 1px solid var(--aw-border);
  margin-bottom: 8px;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 40%, var(--aw-primary-glow), transparent 75%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--aw-accent);
  position: relative;
  /* #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: 只保留 transform/opacity 类廉价动画，
     去掉 hue-rotate 滤镜动画与 conic-gradient 旋转层（持续 GPU 合成会干扰 Edge 最小化） */
  animation: logo-breathe 3s ease-in-out infinite;
}

.logo-mark::before {
  content: '';
  position: absolute;
  inset: -16px;
  border-radius: 50%;
  background: repeating-conic-gradient(from 0deg, rgba(143, 208, 168, 0.6) 0deg 5deg, transparent 5deg 22deg);
  opacity: 0.5;
}

.logo-mark::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid rgba(217, 122, 122, 0.85);
  animation: logo-ripple 3s ease-out infinite;
}

.logo-mark :deep(.ivu-icon) {
  font-size: 20px;
  animation: logo-pulse 1.1s ease-in-out infinite;
}

@keyframes logo-breathe {
  0%, 100% { box-shadow: 0 0 8px rgba(143, 208, 168, 0.35); }
  50% { box-shadow: 0 0 30px rgba(143, 208, 168, 0.6), 0 0 60px rgba(143, 208, 168, 0.32); }
}

@keyframes logo-ripple {
  0% { transform: scale(1); opacity: 0.8; }
  100% { transform: scale(1.9); opacity: 0; }
}

@keyframes logo-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.3); }
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 2px;
  color: var(--aw-text);
  white-space: nowrap;
}

.header {
  height: 64px;
  background: var(--aw-header);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--aw-border);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  transition: background var(--aw-transition);
}

.header-title {
  font-size: 17px;
  font-weight: 500;
  color: var(--aw-text);
  letter-spacing: 1px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--aw-text-secondary);
  padding: 5px 10px;
  border-radius: 8px;
  transition: all var(--aw-transition);
}

.user-trigger:hover {
  color: var(--aw-accent);
  background: var(--aw-panel-hover);
}

.user-name {
  font-size: 13px;
}

.user-arrow {
  color: var(--aw-text-faint);
  transition: transform var(--aw-transition);
}

.divider {
  width: 1px;
  height: 18px;
  background: var(--aw-border);
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--aw-text-secondary);
  transition: all var(--aw-transition);
}

.icon-btn:hover {
  color: var(--aw-accent);
  background: var(--aw-panel-hover);
}

.content {
  padding: 24px;
  background: var(--aw-bg);
  min-height: calc(100vh - 64px);
  transition: background var(--aw-transition);
}

.menu-item span {
  display: inline-block;
  overflow: hidden;
  width: 100px;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  transition: width 0.2s ease 0.2s;
}

.collapsed-menu span {
  width: 0;
  transition: width 0.2s ease;
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.25s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
