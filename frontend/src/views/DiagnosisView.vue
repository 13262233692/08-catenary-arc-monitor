<template>
  <div class="diagnosis-page">
    <div class="detail-header">
      <div class="header-left">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回总览
        </el-button>
        <h2 class="section-title">{{ sectionDetail?.sectionName || '加载中...' }}</h2>
        <el-tag :type="statusTagType" effect="dark" size="large" class="status-tag">
          {{ statusLabel }}
        </el-tag>
        <el-tag type="warning" effect="plain" size="large" class="mode-tag" v-if="diagnosisMode">
          <el-icon><VideoCamera /></el-icon>
          诊断模式
        </el-tag>
      </div>
      <div class="header-right">
        <el-select v-model="timeRange" size="default" @change="handleTimeRangeChange">
          <el-option label="最近1分钟" value="1m" />
          <el-option label="最近5分钟" value="5m" />
          <el-option label="最近15分钟" value="15m" />
          <el-option label="最近1小时" value="1h" />
          <el-option label="最近6小时" value="6h" />
          <el-option label="最近24小时" value="24h" />
          <el-option label="自定义" value="custom" />
        </el-select>
        <div class="ws-indicator">
          <span class="ws-dot" :class="wsConnected ? 'connected' : 'disconnected'"></span>
          <span class="ws-text">{{ wsConnected ? '实时' : '离线' }}</span>
        </div>
        <el-button
          :type="diagnosisMode ? 'danger' : 'primary'"
          size="default"
          @click="toggleDiagnosisMode">
          <el-icon><VideoCamera /></el-icon>
          {{ diagnosisMode ? '退出诊断' : '录像诊断' }}</el-button>
      </div>
    </div>

    <div class="detail-body">
      <div class="info-row" v-if="sectionDetail">
        <el-card class="info-card" shadow="never">
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">线路名称</span>
              <span class="info-value">{{ sectionDetail.lineName || '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">里程范围</span>
              <span class="info-value">K{{ sectionDetail.startKm ?? '--' }} ~ K{{ sectionDetail.endKm ?? '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">所属局</span>
              <span class="info-value">{{ sectionDetail.bureauId || '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">所属站段</span>
              <span class="info-value">{{ sectionDetail.stationId || '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">所属工区</span>
              <span class="info-value">{{ sectionDetail.workAreaId || '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">传感器数量</span>
              <span class="info-value">{{ sectionDetail.sensorCount ?? '--' }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <div class="diagnosis-stats-row" v-if="diagnosisStats">
        <el-card class="stats-card" shadow="never">
          <div class="stats-grid">
            <div class="stat-block">
              <div class="stat-num total">{{ diagnosisStats.totalEvents || 0 }}</div>
              <div class="stat-title">超标事件</div>
            </div>
            <div class="stat-block">
              <div class="stat-num critical">{{ diagnosisStats.criticalCount || 0 }}</div>
              <div class="stat-title">严重</div>
            </div>
            <div class="stat-block">
              <div class="stat-num alarm">{{ diagnosisStats.alarmCount || 0 }}</div>
              <div class="stat-title">报警</div>
            </div>
            <div class="stat-block">
              <div class="stat-num warning">{{ diagnosisStats.warningCount || 0 }}</div>
              <div class="stat-title">预警</div>
            </div>
            <div class="stat-block">
              <div class="stat-num avg">{{ (diagnosisStats.avgDuration || 0).toFixed(0) }}ms</div>
              <div class="stat-title">平均时长</div>
            </div>
            <div class="stat-block">
              <div class="stat-num peak">{{ (diagnosisStats.maxPeak || 0).toFixed(1) }}W</div>
              <div class="stat-title">峰值强度</div>
            </div>
          </div>
        </el-card>
      </div>

      <div :class="['content-row', { 'diagnosis-active': diagnosisMode }]">
        <div :class="['main-panel', { 'with-events': diagnosisMode }]">
          <div class="video-panel" v-if="diagnosisMode" :class="[{ 'video-only': !selectedEvent && !currentVideo }]">
            <div class="panel-header">
              <div class="panel-title">
                <el-icon><VideoCamera /></el-icon>
                车顶监控视频
              </div>
              <div class="sync-status" :class="isSynced ? 'synced' : 'unsynced'">
                <span class="sync-dot"></span>
                {{ isSynced ? '波形-视频已同步' : '等待同步...' }}
                <span class="drift-info" v-if="syncDrift !== null && syncDrift !== undefined">
                  偏差: {{ Math.abs(syncDrift).toFixed(0) }}ms</span>
              </div>
            </div>
            <div class="video-body">
              <HlsVideoPlayer
                v-if="currentVideo"
                :src="currentVideo.hlsPlaylistUrl || defaultDemoVideoUrl"
                :startTime="currentVideo ? currentVideo.startTime : videoAnchorEpoch"
                :markers="videoMarkers"
                :cursorTime="syncer.chartCursorTime.value"
                :autoPlay="true"
                :playSpeed="1.0"
                :height="videoHeight"
                @timeUpdate="handleVideoTimeUpdate"
                @seek="handleVideoSeek"
                @dragStart="syncer.startVideoSeek"
                @dragEnd="handleVideoDragEnd"
                @play="onVideoPlay"
                @pause="onVideoPause"
              />
              <div v-else class="video-placeholder">
                <el-icon :size="64" color="#5a5f78"><VideoPlay /></el-icon>
                <p>请在右侧选择超标事件或拖动图表游标开始诊断</p>
                <p class="hint">视频与波形将实现毫秒级十字游标绝对同步</p>
              </div>
            </div>
            <div class="video-info-row" v-if="currentVideo">
              <div class="info-block">
                <span class="label">车次:</span>
                <span class="value">{{ currentVideo.trainId || 'G1234' }}</span>
              </div>
              <div class="info-block">
                <span class="label">摄像头:</span>
                <span class="value">{{ currentVideo.cameraId || 'ROOF-CAM-01' }}</span>
              </div>
              <div class="info-block">
                <span class="label">分辨率:</span>
                <span class="value">{{ currentVideo.resolution || '1920x1080' }}</span>
              </div>
              <div class="info-block">
                <span class="label">时长:</span>
                <span class="value">{{ ((currentVideo.durationMs || 0)/1000).toFixed(1) }}s</span>
              </div>
            </div>
          </div>

          <div class="chart-panel">
            <div class="panel-header">
              <div class="panel-title">
                <el-icon><TrendCharts /></el-icon>
                电弧放电强度时序波形
              </div>
              <div class="cursor-info" v-if="syncer.videoAbsoluteTime.value">
                <span class="cursor-label">游标时间:</span>
                <span class="cursor-time">{{ formatEpoch(syncer.videoAbsoluteTime.value) }}</span>
                <span class="cursor-intensity" v-if="cursorIntensity !== null && cursorIntensity !== undefined">
                  {{ cursorIntensity.toFixed(3) }} W</span>
              </div>
            </div>
            <div class="chart-body">
              <ArcTimeSeriesChart
                :data="arcData"
                :height="diagnosisMode ? 360 : 420"
                :title="''"
                :warningThreshold="80"
                :alarmThreshold="120"
                :maxDisplayPoints="1200"
                :liveMode="!diagnosisMode"
                :timeRangeMs="currentTimeRangeMs"
                :connected="wsConnected"
                :sectionId="sectionId"
                :cursorTime="syncer.videoAbsoluteTime.value || syncer.chartCursorTime.value"
                :highlightRanges="highlightRanges"
                :readOnly="false"
                @cursor-change="handleChartCursorChange"
                @range-select="handleChartRangeSelect"
                @data-point-click="handleDataPointClick"
                @range-change="handleChartRangeChange"
              />
            </div>
          </div>
        </div>

        <div class="side-panel">
          <div class="side-card">
            <div class="side-title">
              <el-icon><WarningFilled /></el-icon>
              历史超标事件
            </div>
            <ArcAlarmEventList
              :sectionId="sectionId"
              :timeRangeStart="currentRange.start"
              :timeRangeEnd="currentRange.end"
              :selectedEventId="selectedEvent?.eventId"
              @select-event="handleSelectEvent"
            />
          </div>

          <div class="side-card">
            <div class="side-title">
              <el-icon><DataBoard /></el-icon>
              实时统计
            </div>
            <div class="stats-body">
              <div class="stat-row">
                <span class="label">最新强度</span>
                <span class="value latest" :class="intensityClass">{{ latestIntensity }}</span>
              </div>
              <div class="stat-row">
                <span class="label">平均强度</span>
                <span class="value">{{ avgIntensity }}</span>
              </div>
              <div class="stat-row">
                <span class="label">最大强度</span>
                <span class="value max">{{ maxIntensity }}</span>
              </div>
              <div class="stat-row">
                <span class="label">传感器数</span>
                <span class="value">{{ sectionDetail?.sensorCount ?? '--' }}</span>
              </div>
              <div class="stat-row">
                <span class="label">报警次数</span>
                <span class="value alarm">{{ sectionDetail?.alarmCount ?? 0 }}</span>
              </div>
              <div class="stat-row">
                <span class="label">数据点数</span>
                <span class="value">{{ arcData.length }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSectionsStore } from '@/stores/sections'
import { useWebSocket } from '@/composables/useWebSocket'
import { useVideoWaveformSync } from '@/composables/useVideoWaveformSync'
import { queryArcData } from '@/api/arcData'
import { getEventDetail, getDiagnosisStats } from '@/api/diagnosis'
import ArcTimeSeriesChart from '@/components/ArcTimeSeriesChart.vue'
import ArcAlarmEventList from '@/components/ArcAlarmEventList.vue'
import HlsVideoPlayer from '@/components/HlsVideoPlayer.vue'

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
const cursorIntensity = ref(null)
const videoHeight = 280

const diagnosisMode = ref(false)
const selectedEvent = ref(null)
const currentVideo = ref(null)
const videoAnchorEpoch = ref(Date.now() - 30 * 60 * 1000)
const diagnosisStats = ref(null)
const isSynced = ref(false)
const syncDrift = ref(0)

const defaultDemoVideoUrl = ''

const syncer = useVideoWaveformSync()

const currentRange = reactive({
  start: Date.now() - 15 * 60 * 1000,
  end: Date.now()
})

const currentTimeRangeMs = computed(() => currentRange.end - currentRange.start)

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

const intensityClass = computed(() => {
  const val = parseFloat(latestIntensity.value)
  if (isNaN(val)) return ''
  if (val >= 120) return 'alarm'
  if (val >= 80) return 'warning'
  return 'normal'
})

const videoMarkers = computed(() => {
  if (!selectedEvent.value) return []
  return [{
    timestamp: selectedEvent.value.startTime,
    label: `${selectedEvent.value.level} 事件开始`,
    color: selectedEvent.value.level === 'CRITICAL' ? '#ff4d4f' :
            selectedEvent.value.level === 'ALARM' ? '#fa8c16' : '#fadb14'
  }]
})

const highlightRanges = computed(() => {
  const ranges = []
  if (selectedEvent.value) {
    const color = selectedEvent.value.level === 'CRITICAL' ? 'rgba(255,77,79,0.25)' :
                  selectedEvent.value.level === 'ALARM' ? 'rgba(250,140,22,0.2)' : 'rgba(250,219,20,0.2)'
    ranges.push({
      start: selectedEvent.value.startTime,
      end: selectedEvent.value.endTime,
      color,
      label: selectedEvent.value.level
    })
  }
  return ranges
})

watch(() => syncer.videoAbsoluteTime.value, (v) => {
  if (v && syncer.chartCursorTime.value) {
    syncDrift.value = syncer.cursorDriftMs.value
    isSynced.value = Math.abs(syncDrift.value) < 200
  }
})

watch(() => syncer.chartCursorTime.value, (v) => {
  if (v) {
    const v0 = syncer.videoAbsoluteTime.value || v
    syncDrift.value = syncer.cursorDriftMs.value
    isSynced.value = Math.abs(syncDrift.value) < 200
  }
})

function formatEpoch(epochMs) {
  if (!epochMs) return '--:--:--.---'
  const d = new Date(epochMs)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}.${String(d.getMilliseconds()).padStart(3, '0')}`
}

function goBack() {
  router.push('/')
}

function toggleDiagnosisMode() {
  diagnosisMode.value = !diagnosisMode.value
  if (!diagnosisMode.value) {
    selectedEvent.value = null
    currentVideo.value = null
    syncer.chartCursorTime.value = null
  }
}

function getTimeRangeMs() {
  const now = Date.now()
  const map = {
    '1m': 60 * 1000,
    '5m': 5 * 60 * 1000,
    '15m': 15 * 60 * 1000,
    '1h': 60 * 60 * 1000,
    '6h': 6 * 60 * 60 * 1000,
    '24h': 24 * 60 * 60 * 1000
  }
  const delta = map[timeRange.value] || map['15m']
  return { startMs: now - delta, endMs: now, delta }
}

function handleTimeRangeChange() {
  if (timeRange.value !== 'custom') {
    fetchArcData()
    fetchStats()
  }
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
    currentRange.start = startMs
    currentRange.end = endMs
    const res = await queryArcData({
      sectionId: sectionId.value,
      startMs,
      endMs,
      interval: timeRange.value === '24h' ? '5s' :
                timeRange.value === '6h' ? '2s' : '500ms',
      maxPoints: 3000
    })
    arcData.value = res.data || []
    computeStats()
  } catch {
    arcData.value = []
  }
}

async function fetchStats() {
  try {
    const { startMs, endMs } = getTimeRangeMs()
    const res = await getDiagnosisStats(sectionId.value, startMs, endMs)
    diagnosisStats.value = res.data
  } catch {
    diagnosisStats.value = null
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

async function handleSelectEvent(event) {
  selectedEvent.value = event
  diagnosisMode.value = true
  try {
    const res = await getEventDetail(event.eventId)
    const { video, eventVideoTimeOffsetMs } = res.data
    currentVideo.value = video
    if (video) {
      syncer.setVideoStartTime(video.startTime)
    }
    const center = event.startTime
    const halfWindow = Math.max(30000, event.durationMs * 4)
    currentRange.start = center - halfWindow
    currentRange.end = center + halfWindow
    try {
      const res2 = await queryArcData({
        sectionId: sectionId.value,
        startMs: currentRange.start,
        endMs: currentRange.end,
        interval: event.durationMs < 2000 ? '10ms' : event.durationMs < 10000 ? '50ms' : '200ms',
        maxPoints: 5000
      })
      arcData.value = res2.data || []
      computeStats()
    } catch { /* keep current data */ }
    const startTime = event.startTime - 1000
    syncer.forceSync('chart-to-video')
    syncer.updateChartCursor(startTime, true)
  } catch (err) {
    ElMessage.warning('视频数据加载失败')
  }
}

function handleVideoTimeUpdate({ currentTime, absoluteTime }) {
  syncer.updateVideoTime(absoluteTime, false)
}

function handleVideoSeek({ currentTime, absoluteTime }) {
  syncer.updateVideoTime(absoluteTime, false)
}

function handleVideoDragEnd() {
  syncer.endVideoSeek()
}

function onVideoPlay() { /* could add UI state */ }
function onVideoPause() { /* could add UI state */ }

function handleChartCursorChange({ timestamp, value, fromUser }) {
  cursorIntensity.value = value ?? null
  if (fromUser && !diagnosisMode.value) {
    return
  }
  if (fromUser) {
    syncer.updateChartCursor(timestamp, true)
    if (!currentVideo.value) {
      loadVideoForTimestamp(timestamp)
    }
  }
}

async function loadVideoForTimestamp(timestamp) {
  if (!diagnosisMode.value) return
  try {
    const res = await getVideoByTimestamp(sectionId.value, timestamp)
    if (res.data) {
      currentVideo.value = res.data
      syncer.setVideoStartTime(res.data.startTime)
    }
  } catch { /* no video available */ }
}

function handleChartRangeSelect({ start, end }) {
  // user selected a range - could zoom into this time window
  currentRange.start = start
  currentRange.end = end
}

function handleDataPointClick(point) {
  if (point && point.intensity >= 80 && !selectedEvent.value && diagnosisMode.value) {
    loadVideoForTimestamp(point.timestamp || point.x)
  }
}

function handleChartRangeChange({ start, end }) {
  // user zoomed/panned via dataZoom
  currentRange.start = start
  currentRange.end = end
}

watch(lastMessage, (msg) => {
  if (!msg || msg.sectionId !== sectionId.value) return
  if (msg.intensity !== undefined) {
    latestIntensity.value = msg.intensity.toFixed(4)
    if (diagnosisMode.value && selectedEvent.value) {
      arcData.value.push({
        timestamp: msg.timestamp,
        intensity: msg.intensity,
        voltage: msg.voltage,
        current: msg.current,
        temperature: msg.temperature
      })
      const cutoff = currentRange.start
      arcData.value = arcData.value.filter(d => d.timestamp >= cutoff)
    } else if (!diagnosisMode.value) {
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
  fetchStats()
})

onUnmounted(() => {
  wsUnsubscribe(sectionId.value)
  wsDisconnect()
})
</script>

<style scoped>
.diagnosis-page {
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

.header-left, .header-right {
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

.status-tag, .mode-tag {
  font-weight: 600;
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

.info-card, .stats-card {
  background: #1a1d2e !important;
  border: 1px solid #2a2d42 !important;
}

:deep(.el-card__body) {
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

.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.stat-block {
  text-align: center;
  padding: 6px;
  border-radius: 8px;
  background: rgba(255,255,255,0.02);
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-num.total { color: #409eff; }
.stat-num.critical { color: #ff4d4f; }
.stat-num.alarm { color: #fa8c16; }
.stat-num.warning { color: #fadb14; }
.stat-num.avg { color: #36cfc9; }
.stat-num.peak { color: #eb2f96; }

.stat-title {
  font-size: 12px;
  color: #8b8fa3;
  margin-top: 4px;
}

.content-row {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
}

.main-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.main-panel.with-events {
  flex-direction: column;
}

.video-panel, .chart-panel {
  background: #1a1d2e;
  border: 1px solid #2a2d42;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #252840;
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #e0e3eb;
}

.sync-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
}

.sync-status.synced {
  background: rgba(103, 194, 58, 0.1);
  color: #67c23a;
  border: 1px solid rgba(103, 194, 58, 0.2);
}

.sync-status.unsynced {
  background: rgba(245, 108, 108, 0.1);
  color: #f56c6c;
  border: 1px solid rgba(245, 108, 108, 0.2);
}

.sync-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.cursor-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #8b8fa3;
  font-family: 'Consolas', 'Monaco', monospace;
}

.cursor-time {
  color: #409eff;
  font-weight: 600;
}

.cursor-intensity {
  color: #e0e3eb;
  background: rgba(64,158,255,0.1);
  padding: 2px 8px;
  border-radius: 4px;
}

.video-body {
  flex: 1;
  min-height: 0;
  display: flex;
  background: #000;
}

.video-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #5a5f78;
  text-align: center;
  gap: 8px;
}

.video-placeholder p {
  margin: 0;
  font-size: 13px;
}

.video-placeholder .hint {
  font-size: 11px;
  color: #3f435c;
}

.video-info-row {
  display: flex;
  justify-content: space-around;
  padding: 8px 16px;
  background: rgba(0,0,0,0.3);
  border-top: 1px solid #252840;
}

.info-block {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #8b8fa3;
}

.info-block .label {
  color: #5a5f78;
}

.info-block .value {
  color: #cfd3dc;
  font-weight: 600;
}

.chart-body {
  padding: 4px;
}

.side-panel {
  width: 280px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex-shrink: 0;
}

.side-card {
  background: #1a1d2e;
  border: 1px solid #2a2d42;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.side-card:first-child {
  flex: 1;
  min-height: 0;
}

.side-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #e0e3eb;
  padding: 10px 14px;
  border-bottom: 1px solid #252840;
  flex-shrink: 0;
}

.stats-body {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.stat-row .label {
  color: #8b8fa3;
}

.stat-row .value {
  color: #e0e3eb;
  font-weight: 600;
  font-family: 'Consolas', monospace;
}

.stat-row .value.normal { color: #67c23a; }
.stat-row .value.warning { color: #e6a23c; }
.stat-row .value.alarm { color: #f56c6c; }
.stat-row .value.max { color: #e6a23c; }

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
