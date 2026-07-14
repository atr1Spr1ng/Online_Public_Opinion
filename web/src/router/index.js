import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '首页', icon: 'HomeFilled' } },
      { path: 'crawler/batch-collect', name: 'BatchCollect', component: () => import('@/views/crawler/batch-collect.vue'), meta: { title: '批量采集', icon: 'Collection' } },
      { path: 'crawler/hot-list', name: 'HotList', component: () => import('@/views/crawler/hot-list.vue'), meta: { title: '热搜榜单', icon: 'TrendCharts' } },
      { path: 'content/clean', name: 'CleanArticles', component: () => import('@/views/content/clean.vue'), meta: { title: '清洗文章', icon: 'Brush' } },
      { path: 'analysis/sentiment', name: 'Sentiment', component: () => import('@/views/analysis/sentiment.vue'), meta: { title: '情感分析', icon: 'TrendCharts' } },
      { path: 'event', name: 'Event', component: () => import('@/views/event/index.vue'), meta: { title: '事件管理', icon: 'Opportunity' } },
      { path: 'event/:id', name: 'EventDetail', component: () => import('@/views/event/detail.vue'), meta: { title: '事件详情', icon: 'Opportunity' } },
      { path: 'fake', name: 'FakeDetection', component: () => import('@/views/fake/index.vue'), meta: { title: '虚假检测', icon: 'WarningFilled' } },
      { path: 'user/profile', name: 'UserProfile', component: () => import('@/views/user/profile.vue'), meta: { title: '个人信息', icon: 'User' } },
      { path: 'search', name: 'Search', component: () => import('@/views/search/index.vue'), meta: { title: '事件检索', icon: 'Search' } },
      { path: 'user/preferences', name: 'UserPreferences', component: () => import('@/views/user/preferences.vue'), meta: { title: '偏好管理', icon: 'Setting' } },
      { path: 'admin/users', name: 'AdminUsers', component: () => import('@/views/admin/Users.vue'), meta: { title: '用户管理', requireAdmin: true } },
      { path: 'admin/monitor', name: 'AdminMonitor', component: () => import('@/views/admin/Monitor.vue'), meta: { title: '系统监控', requireAdmin: true } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 网络舆情智能分析系统` : '网络舆情智能分析系统'
  const userStore = useUserStore()
  const isAdmin = userStore.userInfo?.role === 'ADMIN'
  if (to.path !== '/login' && !userStore.token) {
    next('/login')
  } else if (to.path === '/login' && userStore.token) {
    next(isAdmin ? '/admin/monitor' : '/dashboard')
  } else if (to.path === '/dashboard' && isAdmin) {
    next('/admin/monitor')
  } else if (to.meta.requireAdmin && !isAdmin) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
