<template>
  <div class="section-detail-page">
    <div class="detail-header">
      <div class="header-left">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回总览
        </el-button>
        <h2 class="section-title">{{ sectionDetail?.sectionName || '加载中...' }}</h2>
        <el-tag
          :type="statusTagType"
          effect="dark"
          size="large"
          class="status-tag"
        >
          {{ statusLabel }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-select v-model="timeRange" size="default" @change="handleTimeRangeChange">
          <el-option label="最近1分钟" value="1m" />
          <el-option label="最近5分钟" value="5m" />
          <el-option label="最近15分钟" value="15m" />
          <el-option label="最近1小时" value="1h" />
          <el-option label="自定义" value="custom" />
        </el-select>
        <div class="ws-indicator">
          <span class="ws-dot" :class="wsConnected ? 'connected' : 'disconnected'"></span>
          <span class="ws-text">{{ wsConnected ? '实时' : '离线' }}</span>
        </div>
      </div>
    </div>

    <div class="detail-body">
      <div class="info-row">
        <el-card class="info-card" shadow="never">
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">线路名称</span>
              <span class="info-value">{{ sectionDetail?.lineName || '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">里程范围</span>
              <span class="info-value">K{{ sectionDetail?.startKm ?? '--' }} ~ K{{ sectionDetail?.endKm ?? '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">所属局</span>
              <span class="info-value">{{ sectionDetail?.bureauId || '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">所属站段</span>
              <span class="info-value">{{ sectionDetail?.stationId || '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">所属工区</span>
              <span class="info-value">{{ sectionDetail?.workAreaId || '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">传感器数量</span>
              <span class="info-value">{{ sectionDetail?.sensorCount ?? '--' }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <div class="content-row">
        <div class="chart-panel">
          <ArcTimeSeriesChart
            :data="arcData"
            :height="420"
            :title="chartTitle"
          />
        </div>
        <div class="stats-sidebar">
          <div class="stats-card">
            <div class="stats-card-title">最新电弧强度</div>
            <div class="stats-card-value latest-value" :class="intensityClass">
              {{ latestIntensity }}
            </div>
          </div>
          <div class="stats-card">
            <div class="stats-card-title">平均强度</div>
            <div class="stats-card-value">{{ avgIntensity }}</div>
          </div>
          <div class="stats-card">
            <div class="stats-card-title">最大强度</div>
            <div class="stats-card-value max-value">{{ maxIntensity }}</div>
          </div>
          <div class="stats-card">
            <div class="stats-card-title">传感器数量</div>
            <div class="stats-card-value">{{ sectionDetail?.sensorCount ?? '--' }}</div>
          </div>
          <div class="stats-card">
            <div class="stats-card-title">报警次数</div>
            <div class="stats-card-value alarm-count">{{ sectionDetail?.alarmCount ?? 0 }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSectionsStore } from '@/stores/sections'
import { useWebSocket } from '@/composables/useWebSocket'
import { queryArcData } from '@/api/arcData'
import ArcTimeSeriesChart from '@/components/ArcTimeSeriesChart.vue'

const route = useRoute()
const router = useRouter()
const sectionsStore = useSectionsStore()
const { connected: wsConnected, connect: wsConnect, subscribe: wsSubscribe, unsubscribe: wsUnsubscribe, disconnect: wsDisconnect, lastMessage } = useWebSocket()

const sectionId = computed(() => route.params.id)
const sectionDetail = ref(null)
const arcData = ref([])
const timeRange = ref('15m')
const latestIntensity = ref('--')
const avgIntensity = ref('--')
const maxIntensity = ref('--')

const statusTagType = computed(() => {
  const s = sectionDetail.value?.status?.toUpperCase()
  if (s === 'ALARM') return 'danger'
  if (s === 'WARNING') return 'warning'
  return 'success'
})

const statusLabel = computed(() => {
  const s = sectionDetail.value?.status?.toUpperCase()
  if (s === 'ALARM') return '报警'
  if (s === 'WARNING') return '预警'
  return '正常'
})

const chartTitle = computed(() => {
  return `${sectionDetail.value?.sectionName || ''} - 电弧强度趋势`
})

const intensityClass = computed(() => {
  const val = parseFloat(latestIntensity.value)
  if (isNaN(val)) return ''
  if (val >= 0.8) return 'intensity-alarm'
  if (val >= 0.5) return 'intensity-warning'
  return 'intensity-normal'
})

function goBack() {
  router.push('/')
}

function getTimeRangeMs() {
  const now = Date.now()
  const map = {
    '1m': 60 * 1000,
    '5m': 5 * 60 * 1000,
    '15m': 15 * 60 * 1000,
    '1h': 60 * 60 * 1000
  }
  const delta = map[timeRange.value] || map['15m']
  return { startMs: now - delta, endMs: now }
}

async function fetchSectionDetail() {
  try {
    const data = await sectionsStore.fetchSectionDetail(sectionId.value)
    sectionDetail.value = data
  } catch {
    sectionDetail.value = null
  }
}

async function fetchArcData() {
  try {
    const { startMs, endMs } = getTimeRangeMs()
    const res = await queryArcData({
      sectionId: sectionId.value,
      startMs,
      endMs,
      interval: '1s',
      maxPoints: 2000
    })
    arcData.value = res.data || []
    computeStats()
  } catch {
    arcData.value = []
  }
}

function computeStats() {
  if (!arcData.value.length) {
    latestIntensity.value = '--'
    avgIntensity.value = '--'
    maxIntensity.value = '--'
    return
  }
  const intensities = arcData.value.map(d => d.intensity)
  const latest = intensities[intensities.length - 1]
  const sum = intensities.reduce((a, b) => a + b, 0)
  const max = Math.max(...intensities)
  latestIntensity.value = latest?.toFixed(4) || '--'
  avgIntensity.value = (sum / intensities.length).toFixed(4)
  maxIntensity.value = max?.toFixed(4) || '--'
}

function handleTimeRangeChange() {
  if (timeRange.value !== 'custom') {
    fetchArcData()
  }
}

watch(lastMessage, (msg) => {
  if (!msg || msg.sectionId !== sectionId.value) return
  if (msg.intensity !== undefined) {
    latestIntensity.value = msg.intensity.toFixed(4)
    arcData.value.push({
      timestamp: msg.timestamp,
      intensity: msg.intensity,
      voltage: msg.voltage,
      current: msg.current,
      temperature: msg.temperature
    })
    const cutoff = Date.now() - 15 * 60 * 1000
    arcData.value = arcData.value.filter(d => d.timestamp >= cutoff)
    computeStats()
  }
  if (msg.status && sectionDetail.value) {
    sectionDetail.value.status = msg.status
  }
})

onMounted(async () => {
  await fetchSectionDetail()
  wsConnect()
  wsSubscribe(sectionId.value)
  fetchArcData()
})

onUnmounted(() => {
  wsUnsubscribe(sectionId.value)
  wsDisconnect()
})
</script>

<style scoped>
.section-detail-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0d0f1a;
  overflow: hidden;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #141728;
  border-bottom: 1px solid #1e2138;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: #e0e3eb;
  margin: 0;
}

.status-tag {
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-right :deep(.el-select) {
  width: 150px;
}

.header-right :deep(.el-select .el-input__wrapper) {
  background: #1a1d2e;
  border-color: #2a2d42;
  box-shadow: none;
}

.ws-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ws-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.ws-dot.connected {
  background: #67c23a;
  box-shadow: 0 0 6px rgba(103, 194, 58, 0.6);
}

.ws-dot.disconnected {
  background: #f56c6c;
  animation: blink 1s infinite;
}

.ws-text {
  font-size: 12px;
  color: #8b8fa3;
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  background: #1a1d2e !important;
  border: 1px solid #2a2d42 !important;
}

.info-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: #8b8fa3;
}

.info-value {
  font-size: 14px;
  font-weight: 600;
  color: #e0e3eb;
}

.content-row {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
}

.chart-panel {
  flex: 1;
  background: #1a1d2e;
  border: 1px solid #2a2d42;
  border-radius: 8px;
  padding: 8px;
  min-width: 0;
}

.stats-sidebar {
  width: 180px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex-shrink: 0;
}

.stats-card {
  background: #1a1d2e;
  border: 1px solid #2a2d42;
  border-radius: 8px;
  padding: 14px 16px;
}

.stats-card-title {
  font-size: 12px;
  color: #8b8fa3;
  margin-bottom: 6px;
}

.stats-card-value {
  font-size: 22px;
  font-weight: 700;
  color: #e0e3eb;
}

.latest-value.intensity-normal {
  color: #67c23a;
}

.latest-value.intensity-warning {
  color: #e6a23c;
}

.latest-value.intensity-alarm {
  color: #f56c6c;
}

.max-value {
  color: #e6a23c;
}

.alarm-count {
  color: #f56c6c;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
