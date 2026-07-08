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
      { path: 'crawler/sources', name: 'Sources', component: () => import('@/views/crawler/sources.vue'), meta: { title: '新闻源管理', icon: 'Collection' } },
      { path: 'crawler/tasks', name: 'Tasks', component: () => import('@/views/crawler/tasks.vue'), meta: { title: '采集任务', icon: 'Coin' } },
      { path: 'crawler/articles', name: 'Articles', component: () => import('@/views/crawler/articles.vue'), meta: { title: '原始文章', icon: 'Document' } },
      { path: 'content/clean', name: 'CleanArticles', component: () => import('@/views/content/clean.vue'), meta: { title: '清洗文章', icon: 'Brush' } },
      { path: 'analysis/sentiment', name: 'Sentiment', component: () => import('@/views/analysis/sentiment.vue'), meta: { title: '情感分析', icon: 'TrendCharts' } },
      { path: 'event', name: 'Event', component: () => import('@/views/event/index.vue'), meta: { title: '事件管理', icon: 'Opportunity' } },
      { path: 'propagation/source', name: 'PropSource', component: () => import('@/views/propagation/source.vue'), meta: { title: '事件溯源', icon: 'Search' } },
      { path: 'propagation/path', name: 'PropPath', component: () => import('@/views/propagation/path.vue'), meta: { title: '传播路径', icon: 'Share' } },
      { path: 'fake', name: 'FakeDetection', component: () => import('@/views/fake/index.vue'), meta: { title: '虚假检测', icon: 'WarningFilled' } },
      { path: 'report', name: 'Report', component: () => import('@/views/report/index.vue'), meta: { title: '舆情报告', icon: 'DataAnalysis' } }
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
  if (to.path !== '/login' && !userStore.token) {
    next('/login')
  } else if (to.path === '/login' && userStore.token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
