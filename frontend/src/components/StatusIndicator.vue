<template>
  <span class="status-indicator" :class="[sizeClass, { pulse: status === 'ALARM' }]">
    <span class="dot" :style="dotStyle"></span>
    <span v-if="showText" class="label">{{ statusLabel }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: 'OFFLINE' },
  size: { type: String, default: 'medium' },
  showText: { type: Boolean, default: true }
})

const colorMap = {
  NORMAL: '#67c23a',
  WARNING: '#e6a23c',
  ALARM: '#f56c6c',
  OFFLINE: '#909399'
}

const labelMap = {
  NORMAL: '正常',
  WARNING: '警告',
  ALARM: '报警',
  OFFLINE: '离线'
}

const sizeMap = {
  small: 8,
  medium: 12,
  large: 16
}

const dotColor = computed(() => colorMap[props.status] || colorMap.OFFLINE)

const statusLabel = computed(() => labelMap[props.status] || '未知')

const sizeClass = computed(() => `size-${props.size}`)

const dotStyle = computed(() => {
  const px = sizeMap[props.size] || 12
  return {
    width: `${px}px`,
    height: `${px}px`,
    backgroundColor: dotColor.value,
    boxShadow: `0 0 ${px * 0.6}px ${dotColor.value}`
  }
})
</script>

<style scoped>
.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dot {
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.label {
  color: #ccc;
  font-size: 12px;
  line-height: 1;
}

.size-small .label {
  font-size: 11px;
}

.size-large .label {
  font-size: 14px;
}

@keyframes pulse-glow {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(1.3);
  }
}

.pulse .dot {
  animation: pulse-glow 1.2s ease-in-out infinite;
}
</style>
