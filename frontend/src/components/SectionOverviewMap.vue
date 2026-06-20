<template>
  <div class="section-overview-map">
    <div class="map-header">
      <span class="map-title">区段监控总览</span>
      <el-tag size="small" type="info">共 {{ sections.length }} 个区段</el-tag>
    </div>
    <div class="map-grid">
      <div
        v-for="section in sections"
        :key="section.sectionId"
        class="section-block"
        :class="`status-${section.status?.toLowerCase()}`"
        @click="$emit('select', section)"
      >
        <div class="section-name">{{ section.sectionName }}</div>
        <div class="section-intensity">{{ section.latestIntensity?.toFixed(2) || '--' }}</div>
        <div class="section-status-dot" :class="`dot-${section.status?.toLowerCase()}`"></div>
      </div>
      <div v-if="!sections.length" class="empty-map">
        <el-empty description="暂无区段数据" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  sections: {
    type: Array,
    default: () => []
  }
})

defineEmits(['select'])
</script>

<style scoped>
.section-overview-map {
  background: #1a1d2e;
  border-radius: 8px;
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.map-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.map-title {
  font-size: 15px;
  font-weight: 600;
  color: #e0e3eb;
}

.map-grid {
  flex: 1;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
  align-content: start;
}

.section-block {
  position: relative;
  background: #252840;
  border-radius: 6px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.section-block:hover {
  border-color: #409eff;
  transform: translateY(-1px);
}

.section-block.status-normal {
  border-left: 3px solid #67c23a;
}

.section-block.status-warning {
  border-left: 3px solid #e6a23c;
}

.section-block.status-alarm {
  border-left: 3px solid #f56c6c;
  animation: alarm-pulse 1.5s infinite;
}

.section-name {
  font-size: 13px;
  color: #cfd3dc;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.section-intensity {
  font-size: 18px;
  font-weight: 700;
  color: #e0e3eb;
}

.section-status-dot {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-normal {
  background: #67c23a;
}

.dot-warning {
  background: #e6a23c;
}

.dot-alarm {
  background: #f56c6c;
  animation: dot-blink 1s infinite;
}

.empty-map {
  grid-column: 1 / -1;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

@keyframes alarm-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.3); }
  50% { box-shadow: 0 0 8px 2px rgba(245, 108, 108, 0.3); }
}

@keyframes dot-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
