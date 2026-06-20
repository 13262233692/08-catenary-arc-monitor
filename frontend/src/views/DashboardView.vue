<template>
  <el-container class="dashboard-layout">
    <el-aside width="220px" class="sidebar">
      <div class="sidebar-logo">
        <el-icon :size="24" color="#409eff"><Van /></el-icon>
        <span class="logo-text">弓网电弧监测</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        background-color="#141728"
        text-color="#8b8fa3"
        active-text-color="#409eff"
        @select="handleMenuSelect"
      >
        <el-menu-item index="overview">
          <el-icon><Monitor /></el-icon>
          <span>总览监控</span>
        </el-menu-item>
        <el-menu-item index="sections">
          <el-icon><Grid /></el-icon>
          <span>区段管理</span>
        </el-menu-item>
        <el-menu-item index="query">
          <el-icon><Search /></el-icon>
          <span>数据查询</span>
        </el-menu-item>
        <el-menu-item index="settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">
        <div class="ws-status">
          <span class="status-dot" :class="wsConnected ? 'connected' : 'disconnected'"></span>
          <span class="status-text">{{ wsConnected ? '实时连接' : '连接断开' }}</span>
        </div>
        <div class="user-info">
          <el-icon><UserFilled /></el-icon>
          <span class="user-name">{{ authStore.realName || authStore.username || '用户' }}</span>
          <el-button link type="danger" size="small" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </div>
    </el-aside>

    <el-main class="main-content">
      <div class="stats-bar">
        <div class="stat-card total">
          <div class="stat-icon">
            <el-icon :size="28"><Grid /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ sectionsStore.stats.total }}</div>
            <div class="stat-label">总区段数</div>
          </div>
        </div>
        <div class="stat-card normal">
          <div class="stat-icon">
            <el-icon :size="28"><CircleCheck /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ sectionsStore.stats.normal }}</div>
            <div class="stat-label">正常运行</div>
          </div>
        </div>
        <div class="stat-card warning">
          <div class="stat-icon">
            <el-icon :size="28"><Warning /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ sectionsStore.stats.warning }}</div>
            <div class="stat-label">预警区段</div>
          </div>
        </div>
        <div class="stat-card alarm">
          <div class="stat-icon">
            <el-icon :size="28"><CircleClose /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ sectionsStore.stats.alarm }}</div>
            <div class="stat-label">报警区段</div>
          </div>
        </div>
      </div>

      <div class="main-area">
        <div class="map-panel">
          <SectionOverviewMap
            :sections="sectionsStore.sections"
            @select="goToSection"
          />
        </div>
        <div class="status-panel">
          <div class="panel-title">区段状态概览</div>
          <div class="status-list">
            <div
              v-for="section in alarmSections"
              :key="section.sectionId"
              class="status-item alarm-item"
              @click="goToSection(section)"
            >
              <div class="status-dot alarm-dot"></div>
              <div class="status-info">
                <div class="status-name">{{ section.sectionName }}</div>
                <div class="status-detail">强度: {{ section.latestIntensity?.toFixed(3) || '--' }}</div>
              </div>
              <el-tag type="danger" size="small" effect="dark">报警</el-tag>
            </div>
            <div
              v-for="section in warningSections"
              :key="section.sectionId"
              class="status-item warning-item"
              @click="goToSection(section)"
            >
              <div class="status-dot warning-dot"></div>
              <div class="status-info">
                <div class="status-name">{{ section.sectionName }}</div>
                <div class="status-detail">强度: {{ section.latestIntensity?.toFixed(3) || '--' }}</div>
              </div>
              <el-tag type="warning" size="small" effect="dark">预警</el-tag>
            </div>
            <div v-if="!alarmSections.length && !warningSections.length" class="all-normal">
              <el-icon :size="40" color="#67c23a"><CircleCheck /></el-icon>
              <p>所有区段运行正常</p>
            </div>
          </div>
        </div>
      </div>

      <div class="chart-area">
        <ArcTimeSeriesChart
          :data="arcData"
          :height="220"
          title="实时电弧强度趋势"
        />
      </div>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSectionsStore } from '@/stores/sections'
import { useWebSocket } from '@/composables/useWebSocket'
import { queryArcData } from '@/api/arcData'
import SectionOverviewMap from '@/components/SectionOverviewMap.vue'
import ArcTimeSeriesChart from '@/components/ArcTimeSeriesChart.vue'

const router = useRouter()
const authStore = useAuthStore()
const sectionsStore = useSectionsStore()
const { connected: wsConnected, connect: wsConnect, lastMessage } = useWebSocket()

const activeMenu = ref('overview')
const arcData = ref([])

const alarmSections = computed(() =>
  sectionsStore.sections.filter(s => s.status === 'ALARM')
)

const warningSections = computed(() =>
  sectionsStore.sections.filter(s => s.status === 'WARNING')
)

function handleMenuSelect(index) {
  activeMenu.value = index
}

function goToSection(section) {
  router.push({ name: 'SectionDetail', params: { id: section.sectionId } })
}

async function fetchArcTrend() {
  try {
    const endMs = Date.now()
    const startMs = endMs - 15 * 60 * 1000
    const res = await queryArcData({
      sectionId: '',
      startMs,
      endMs,
      interval: '10s',
      maxPoints: 500
    })
    arcData.value = res.data || []
  } catch {
    arcData.value = []
  }
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

watch(lastMessage, (msg) => {
  if (!msg) return
  if (msg.sectionId) {
    sectionsStore.updateSectionStatus(msg.sectionId, msg.status)
  }
})

onMounted(async () => {
  await authStore.fetchUserInfo()
  await sectionsStore.fetchSections()
  wsConnect()
  fetchArcTrend()
})
</script>

<style scoped>
.dashboard-layout {
  height: 100vh;
  background: #0d0f1a;
}

.sidebar {
  background: #141728;
  border-right: 1px solid #1e2138;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  border-bottom: 1px solid #1e2138;
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: #e0e3eb;
  letter-spacing: 1px;
}

.sidebar-menu {
  flex: 1;
  padding-top: 8px;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  margin: 2px 8px;
  border-radius: 6px;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: #1a1d2e !important;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: rgba(64, 158, 255, 0.1) !important;
}

.sidebar-footer {
  border-top: 1px solid #1e2138;
  padding: 12px 16px;
}

.ws-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.connected {
  background: #67c23a;
  box-shadow: 0 0 6px rgba(103, 194, 58, 0.6);
}

.status-dot.disconnected {
  background: #f56c6c;
  box-shadow: 0 0 6px rgba(245, 108, 108, 0.6);
  animation: dot-blink 1s infinite;
}

.status-text {
  font-size: 12px;
  color: #8b8fa3;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8b8fa3;
  font-size: 13px;
}

.user-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  padding: 16px;
  overflow-y: auto;
  background: #0d0f1a;
}

.stats-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #1a1d2e;
  border: 1px solid #2a2d42;
  border-radius: 10px;
  padding: 18px 20px;
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-card.total .stat-icon {
  background: rgba(64, 158, 255, 0.12);
  color: #409eff;
}

.stat-card.normal .stat-icon {
  background: rgba(103, 194, 58, 0.12);
  color: #67c23a;
}

.stat-card.warning .stat-icon {
  background: rgba(230, 162, 60, 0.12);
  color: #e6a23c;
}

.stat-card.alarm .stat-icon {
  background: rgba(245, 108, 108, 0.12);
  color: #f56c6c;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #e0e3eb;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #8b8fa3;
  margin-top: 2px;
}

.main-area {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  height: 380px;
}

.map-panel {
  flex: 6;
  min-width: 0;
}

.status-panel {
  flex: 4;
  background: #1a1d2e;
  border: 1px solid #2a2d42;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #e0e3eb;
  margin-bottom: 12px;
}

.status-list {
  flex: 1;
  overflow-y: auto;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  margin-bottom: 6px;
}

.status-item:hover {
  background: #252840;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.alarm-dot {
  background: #f56c6c;
  animation: dot-blink 1s infinite;
}

.warning-dot {
  background: #e6a23c;
}

.status-info {
  flex: 1;
  min-width: 0;
}

.status-name {
  font-size: 13px;
  color: #cfd3dc;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-detail {
  font-size: 11px;
  color: #8b8fa3;
  margin-top: 2px;
}

.all-normal {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #67c23a;
}

.all-normal p {
  margin-top: 12px;
  font-size: 14px;
  color: #8b8fa3;
}

.chart-area {
  background: #1a1d2e;
  border: 1px solid #2a2d42;
  border-radius: 8px;
  padding: 8px;
}

@keyframes dot-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
