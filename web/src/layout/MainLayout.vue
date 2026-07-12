<template>
  <el-container class="layout">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <span v-if="!isCollapse">网络舆情分析系统</span>
        <span v-else>舆情</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        :collapse="isCollapse"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-sub-menu index="crawler">
          <template #title>
            <el-icon><Collection /></el-icon>
            <span>数据采集</span>
          </template>
          <el-menu-item index="/crawler/source-collect">按新闻源采集</el-menu-item>
          <el-menu-item index="/crawler/topic-collect">按话题采集</el-menu-item>
          <el-menu-item index="/crawler/articles">原始文章</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/content/clean">
          <el-icon><Brush /></el-icon>
          <span>内容清洗</span>
        </el-menu-item>
        <el-menu-item index="/analysis/sentiment">
          <el-icon><TrendCharts /></el-icon>
          <span>情感分析</span>
        </el-menu-item>
        <el-menu-item index="/fake">
          <el-icon><WarningFilled /></el-icon>
          <span>虚假检测</span>
        </el-menu-item>
        <el-menu-item index="/event">
          <el-icon><Opportunity /></el-icon>
          <span>事件管理</span>
        </el-menu-item>
        <el-menu-item index="/search">
          <el-icon><Search /></el-icon>
          <span>文章搜索</span>
        </el-menu-item>
        <el-sub-menu index="user">
          <template #title>
            <el-icon><User /></el-icon>
            <span>个人中心</span>
          </template>
          <el-menu-item index="/user/profile">个人信息</el-menu-item>
          <el-menu-item index="/user/preferences">偏好管理</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="navbar">
        <div class="navbar-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </div>
        <div class="navbar-right">
          <span class="user-name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员' }}</span>
          <el-button type="danger" text @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const activeMenu = computed(() => route.path.replace(/\/\d+$/, ''))

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }
.sidebar { background-color: #304156; overflow-x: hidden; }
.logo {
  height: 60px; line-height: 60px; text-align: center;
  color: #fff; font-size: 18px; font-weight: bold;
  background-color: #2b3a4a; white-space: nowrap;
}
.navbar {
  background: #fff; display: flex; align-items: center;
  justify-content: space-between; border-bottom: 1px solid #e6e6e6;
  height: 50px; padding: 0 20px;
}
.collapse-btn { font-size: 20px; cursor: pointer; }
.navbar-right { display: flex; align-items: center; gap: 12px; }
.user-name { color: #333; }
.main-content { background: #f0f2f5; padding: 20px; }
.el-menu { border-right: none; }
</style>
