<template>
  <div class="arc-alarm-event-list">
    <el-tabs v-model="activeFilter" class="filter-tabs" @tab-change="onFilterChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="严重" name="critical" />
      <el-tab-pane label="预警" name="warning" />
    </el-tabs>
    <div v-if="loading" class="skeleton-list">
      <div v-for="i in 5" :key="i" class="skeleton-card">
        <div class="skeleton-badge"></div>
        <div class="skeleton-content">
          <div class="skeleton-line skeleton-line-1"></div>
          <div class="skeleton-line skeleton-line-2"></div>
          <div class="skeleton-line skeleton-line-3"></div>
        </div>
      </div>
    </div>
    <div v-else-if="filteredEvents.length === 0" class="empty-state">
      <div class="empty-icon">∅</div>
      <div class="empty-text">无超标事件</div>
    </div>
    <div v-else class="event-list">
      <div
        v-for="event in filteredEvents"
        :key="event.id"
        class="event-card"
        :class="{ selected: event.id === selectedEventId }"
        @click="onSelectEvent(event)"
      >
        <div class="event-left">
          <el-tag
            class="level-badge"
            :class="[event.level, { pulse: event.level === 'CRITICAL' || event.level === 'ALARM' }]"
            :type="levelTagType[event.level]"
            size="small"
            effect="dark"
          >{{ levelLabel[event.level] }}</el-tag>
          <div v-if="event.hasVideo" class="video-indicator" title="关联视频">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
              <path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/>
            </svg>
          </div>
        </div>
        <div class="event-info">
          <div class="event-time">{{ formatTime(event.startTime) }}</div>
          <div class="event-meta">
            <span class="meta-item duration">{{ formatDuration(event.duration) }}</span>
            <span class="meta-item peak" :class="{ high: isHighIntensity(event.peakIntensity) }">
              峰值 {{ event.peakIntensity?.toFixed(2) }} W
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useDataQuery } from '@/composables/useDataQuery'
import request from '@/utils/request'

const props = defineProps({
  sectionId: {
    type: String,
    default: ''
  },
  timeRangeStart: {
    type: Number,
    default: 0
  },
  timeRangeEnd: {
    type: Number,
    default: 0
  },
  selectedEventId: {
    type: String,
    default: ''
  },
  intensityThreshold: {
    type: Number,
    default: 150
  }
})

const emit = defineEmits(['select-event'])

const activeFilter = ref('all')

const levelLabel = {
  WARNING: '预警',
  ALARM: '报警',
  CRITICAL: '严重'
}

const levelTagType = {
  WARNING: 'warning',
  ALARM: 'warning',
  CRITICAL: 'danger'
}

const { data, loading, query } = useDataQuery(
  async (params, _signal) => {
    const res = await request.post('/diagnosis/events', params)
    return res || []
  },
  { debounceMs: 200, retries: 2 }
)

const sortedEvents = computed(() => {
  const list = data.value || []
  return [...list].sort((a, b) => b.startTime - a.startTime).slice(0, 50)
})

const filteredEvents = computed(() => {
  const events = sortedEvents.value
  if (activeFilter.value === 'all') return events
  if (activeFilter.value === 'critical') {
    return events.filter(e => e.level === 'CRITICAL' || e.level === 'ALARM')
  }
  if (activeFilter.value === 'warning') {
    return events.filter(e => e.level === 'WARNING')
  }
  return events
})

function isHighIntensity(value) {
  return value && value >= props.intensityThreshold
}

function formatTime(ts) {
  if (!ts) return '--:--:--.---'
  const d = new Date(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  const ms = String(d.getMilliseconds()).padStart(3, '0')
  return `${hh}:${mm}:${ss}.${ms}`
}

function formatDuration(ms) {
  if (!ms || ms < 0) return '0ms'
  if (ms < 1000) return `${Math.round(ms)}ms`
  const s = ms / 1000
  if (s < 60) return `${s.toFixed(1)}s`
  const m = Math.floor(s / 60)
  const rs = (s % 60).toFixed(0)
  return `${m}m ${rs}s`
}

function onSelectEvent(event) {
  emit('select-event', event)
}

function onFilterChange() {
}

function triggerQuery() {
  if (!props.sectionId || !props.timeRangeStart || !props.timeRangeEnd) return
  query({
    sectionId: props.sectionId,
    startTime: props.timeRangeStart,
    endTime: props.timeRangeEnd
  })
}

watch(
  () => [props.sectionId, props.timeRangeStart, props.timeRangeEnd],
  () => {
    triggerQuery()
  },
  { immediate: false }
)

onMounted(() => {
  triggerQuery()
})
</script>

<style scoped>
.arc-alarm-event-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #12151f;
  border: 1px solid #2a2d42;
  border-radius: 6px;
  overflow: hidden;
}

.filter-tabs {
  flex-shrink: 0;
  --el-tabs-header-background: #1a1d2e;
  --el-tabs-border-color: #2a2d42;
  --el-text-color-primary: #cfd3dc;
  --el-text-color-regular: #8b8fa3;
  --el-color-primary: #409eff;
}

.filter-tabs :deep(.el-tabs__header) {
  background: #1a1d2e;
  border-bottom: 1px solid #2a2d42;
  margin: 0;
}

.filter-tabs :deep(.el-tabs__item) {
  font-size: 12px;
  height: 38px;
  line-height: 38px;
  color: #8b8fa3;
}

.filter-tabs :deep(.el-tabs__item.is-active) {
  color: #409eff;
}

.filter-tabs :deep(.el-tabs__active-bar) {
  background-color: #409eff;
  height: 2px;
}

.filter-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.skeleton-list {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton-card {
  display: flex;
  gap: 10px;
  padding: 12px;
  background: #1a1d2e;
  border-radius: 6px;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

.skeleton-badge {
  width: 42px;
  height: 22px;
  border-radius: 4px;
  background: #2a2d42;
  flex-shrink: 0;
}

.skeleton-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-line {
  height: 12px;
  border-radius: 3px;
  background: #2a2d42;
}

.skeleton-line-1 {
  width: 60%;
}

.skeleton-line-2 {
  width: 45%;
  height: 10px;
}

.skeleton-line-3 {
  width: 70%;
  height: 10px;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 30px 20px;
  min-height: 0;
}

.empty-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(139, 143, 163, 0.1);
  border: 1px solid #3a3f5c;
  color: #6b6f80;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 300;
}

.empty-text {
  color: #6b6f80;
  font-size: 13px;
}

.event-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.event-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  background: #1a1d2e;
  border: 1px solid #2a2d42;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.event-card:hover {
  background: #1f2335;
  border-color: #3a3f5c;
  transform: translateX(2px);
}

.event-card.selected {
  background: rgba(64, 158, 255, 0.08);
  border-color: #409eff;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.25);
}

.event-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding-top: 1px;
}

.level-badge {
  font-size: 10px;
  padding: 0 6px;
  height: 20px;
  line-height: 18px;
  font-weight: 600;
}

.level-badge.CRITICAL {
  --el-tag-bg-color: rgba(245, 108, 108, 0.15);
  --el-tag-border-color: #f56c6c;
  --el-tag-text-color: #f56c6c;
}

.level-badge.ALARM {
  --el-tag-bg-color: rgba(230, 162, 60, 0.15);
  --el-tag-border-color: #e6a23c;
  --el-tag-text-color: #e6a23c;
}

.level-badge.WARNING {
  --el-tag-bg-color: rgba(225, 186, 83, 0.15);
  --el-tag-border-color: #e1ba53;
  --el-tag-text-color: #e1ba53;
}

.level-badge.pulse {
  animation: badge-pulse 1.8s ease-in-out infinite;
}

@keyframes badge-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 currentColor;
  }
  50% {
    box-shadow: 0 0 0 4px transparent;
  }
}

.video-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  color: #409eff;
  background: rgba(64, 158, 255, 0.1);
  border-radius: 4px;
  flex-shrink: 0;
}

.event-info {
  flex: 1;
  min-width: 0;
}

.event-time {
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 13px;
  color: #e0e3eb;
  font-weight: 500;
  margin-bottom: 5px;
  letter-spacing: 0.2px;
}

.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 11px;
}

.meta-item {
  color: #8b8fa3;
}

.meta-item.duration {
  font-family: 'SF Mono', Consolas, monospace;
  color: #66b1ff;
}

.meta-item.peak {
  color: #8b8fa3;
  font-family: 'SF Mono', Consolas, monospace;
}

.meta-item.peak.high {
  color: #f56c6c;
  font-weight: 600;
}
</style>
