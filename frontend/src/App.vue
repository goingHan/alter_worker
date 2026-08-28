<template>
  <div class="app">
    <Layout>
      <Sider collapsible :collapsed-width="78" v-model="isCollapsed" class="sider">
        <Menu active-name="dashboard" theme="light" width="auto" :class="menuitemClasses">
          <MenuItem name="dashboard" to="/">
            <Icon type="ios-home" />
            <span>Dashboard</span>
          </MenuItem>
          <MenuItem name="api-keys" to="/api-keys">
            <Icon type="ios-key" />
            <span>API Keys</span>
          </MenuItem>
          <MenuItem name="channels" to="/channels">
            <Icon type="ios-mail" />
            <span>Channels</span>
          </MenuItem>
          <MenuItem name="assignees" to="/assignees">
            <Icon type="ios-people" />
            <span>Assignees</span>
          </MenuItem>
          <MenuItem name="groups" to="/groups">
            <Icon type="ios-contacts" />
            <span>Groups</span>
          </MenuItem>
          <MenuItem name="templates" to="/templates">
            <Icon type="ios-document" />
            <span>Templates</span>
          </MenuItem>
          <MenuItem name="rules" to="/rules">
            <Icon type="ios-alert" />
            <span>Alert Rules</span>
          </MenuItem>
          <MenuItem name="history" to="/history">
            <Icon type="ios-time" />
            <span>Alert History</span>
          </MenuItem>
          <MenuItem name="failed" to="/failed">
            <Icon type="ios-warning" />
            <span>Failed Messages</span>
          </MenuItem>
        </Menu>
      </Sider>
      <Layout>
        <Header class="header">AlertWorker Management Console</Header>
        <Content class="content">
          <router-view />
        </Content>
      </Layout>
    </Layout>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const isCollapsed = ref(false)
const menuitemClasses = computed(() => {
  return ['menu-item', isCollapsed.value ? 'collapsed-menu' : '']
})
</script>

<style scoped>
.app {
  min-height: 100vh;
  background: #f8fafc;
}

.sider {
  background: #ffffff;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.03);
}

.header {
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  font-size: 18px;
  font-weight: 500;
  color: #475569;
  display: flex;
  align-items: center;
  padding-left: 24px;
}

.content {
  padding: 24px;
  background: #f8fafc;
  min-height: calc(100vh - 64px);
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
</style>
