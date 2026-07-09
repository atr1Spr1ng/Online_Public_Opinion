<template>
  <div>
    <el-card>
      <template #header><span>传播路径分析</span></template>
      <div class="search-bar">
        <el-input-number v-model="eventId" :min="1" placeholder="事件ID" style="width:200px" />
        <el-button type="primary" @click="handleAnalyze" :loading="analyzing" style="margin-left:12px">分析传播路径</el-button>
      </div>
      <el-divider />
      <template v-if="result">
        <el-row :gutter="20">
          <el-col :span="6"><el-statistic title="传播深度" :value="result.spreadDepth || 0" /></el-col>
          <el-col :span="6"><el-statistic title="节点总数" :value="result.totalNodes || 0" /></el-col>
          <el-col :span="6"><el-statistic title="持续时间(h)" :value="result.durationHours || 0" /></el-col>
          <el-col :span="6"><el-statistic title="传播速度" :value="result.spreadSpeed || 0" suffix="篇/h" /></el-col>
        </el-row>
        <div style="margin-top:4px;display:flex;gap:12px;align-items:center;font-size:13px;color:#606266">
          <span>分析方法：<el-tag size="small" :type="result.method === 'llm' ? 'success' : 'warning'">{{ result.method === 'llm' ? 'AI 分析' : '规则降级' }}</el-tag></span>
        </div>
        <el-divider>传播图谱</el-divider>
        <div style="margin-bottom:8px;display:flex;gap:16px;font-size:12px;color:#909399">
          <span><span style="display:inline-block;width:12px;height:12px;background:#F56C6C;border-radius:2px;margin-right:4px;vertical-align:middle"></span>官方媒体</span>
          <span><span style="display:inline-block;width:12px;height:12px;background:#67C23A;border-radius:2px;margin-right:4px;vertical-align:middle"></span>社交媒体</span>
          <span><span style="display:inline-block;width:12px;height:12px;background:#409EFF;border-radius:2px;margin-right:4px;vertical-align:middle"></span>商业媒体</span>
          <span style="margin-left:8px">⬟ 关键传播节点</span>
        </div>
        <div ref="graphRef" style="width:100%;height:450px"></div>
      </template>
      <el-empty v-if="!result && !analyzing" description="输入事件ID进行分析" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { analyzePropagation } from '@/api/propagation'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const eventId = ref(1)
const analyzing = ref(false)
const result = ref(null)
const graphRef = ref(null)
let chart = null

async function handleAnalyze() {
  analyzing.value = true
  try {
    const res = await analyzePropagation({ eventId: eventId.value })
    result.value = res.data
    ElMessage.success('分析完成')
    await nextTick()
    renderGraph()
  } catch (e) { ElMessage.error(e.message) }
  finally { analyzing.value = false }
}

function nodeColor(n) {
  if (n.isSource) return '#E6A23C'
  if (n.nodeType === 'official') return '#F56C6C'
  if (n.nodeType === 'social') return '#67C23A'
  return '#409EFF'
}

function renderGraph() {
  if (!graphRef.value || !result.value?.nodes?.length) return
  if (chart) chart.dispose()
  chart = echarts.init(graphRef.value)

  const typeName = { official: '官方媒体', commercial: '商业媒体', social: '社交媒体' }

  const nodes = result.value.nodes.map(n => ({
    id: n.cleanId,
    name: n.articleTitle || `文章#${n.cleanId}`,
    symbolSize: n.isSource ? 44 : n.isInfluencer ? 36 : Math.max(18, 32 - (n.depth || 0) * 3),
    symbol: n.isInfluencer ? 'diamond' : 'circle',
    itemStyle: { color: nodeColor(n), borderColor: n.isInfluencer ? '#333' : 'transparent', borderWidth: n.isInfluencer ? 2 : 0 },
    label: { show: true, fontSize: n.isSource ? 12 : 10, formatter: p => {
      const label = p.name.length > 10 ? p.name.slice(0, 10) + '...' : p.name
      if (n.isSource) return label + '\n源头'
      if (n.isInfluencer) return label + '\n★关键'
      return label
    } }
  }))

  const links = (result.value.edges || []).map(e => ({
    source: String(e.source),
    target: String(e.target),
    lineStyle: { width: Math.max(1, (e.similarity || 0) * 4), opacity: Math.min(1, (e.similarity || 0) + 0.3) }
  }))

  // 如果没有 edges，回退到星形连接
  if (links.length === 0 && result.value.nodes.length > 1) {
    const sourceNode = result.value.nodes.find(n => n.isSource)
    const nonSource = result.value.nodes.filter(n => !n.isSource)
    nonSource.forEach(n => {
      if (sourceNode?.cleanId) {
        links.push({ source: String(sourceNode.cleanId), target: String(n.cleanId), lineStyle: { width: 1, opacity: 0.5 } })
      }
    })
  }

  chart.setOption({
    tooltip: {
      formatter: p => {
        if (p.dataType === 'node') {
          const n = result.value.nodes.find(x => x.cleanId == p.id) || {}
          const tags = [n.isSource && '源头', n.isInfluencer && '关键节点', typeName[n.nodeType]].filter(Boolean).join(' | ')
          return `${p.name}<br/>来源: ${n.sourceName || '未知'}<br/>${tags}`
        }
        return ''
      }
    },
    series: [{
      type: 'graph',
      layout: 'force',
      force: { repulsion: 350, edgeLength: [120, 300], gravity: 0.08 },
      roam: true,
      draggable: true,
      data: nodes,
      links: links,
      lineStyle: { color: '#c0c4cc', curveness: 0.25 }
    }]
  })
}
</script>

<style scoped>
.search-bar { display: flex; align-items: center; margin-bottom: 16px; }
</style>
