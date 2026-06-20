<template>
  <div class="hls-video-player" :style="{ height: height + 'px' }">
    <div v-if="error" class="error-overlay">
      <div class="error-icon">!</div>
      <div class="error-text">{{ errorMessage }}</div>
      <button class="retry-btn" @click="retryLoad">重试</button>
    </div>
    <div v-if="isBuffering" class="buffering-overlay">
      <div class="loading-spinner"></div>
    </div>
    <video
      ref="videoRef"
      class="video-element"
      :muted="muted"
      :autoplay="autoPlay"
      playsinline
      @click="togglePlay"
      @play="onPlay"
      @pause="onPause"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMetadata"
      @waiting="onWaiting"
      @playing="onPlaying"
      @error="onError"
      @seeked="onSeeked"
    ></video>
    <div
      ref="progressContainerRef"
      class="progress-container"
      @mousedown="onProgressMouseDown"
      @mousemove="onProgressMouseMove"
      @mouseleave="onProgressMouseLeave"
    >
      <div class="progress-track">
        <div class="progress-buffered" :style="{ width: bufferedPercent + '%' }"></div>
        <div class="progress-played" :style="{ width: playedPercent + '%' }"></div>
      </div>
      <div
        v-for="(marker, idx) in visibleMarkers"
        :key="idx"
        class="progress-marker"
        :style="{ left: marker.percent + '%', backgroundColor: marker.color }"
        @mouseenter="hoverMarker = marker"
        @mouseleave="hoverMarker = null"
      >
        <div v-if="hoverMarker === marker" class="marker-tooltip">{{ marker.label }}</div>
      </div>
      <div
        v-if="cursorPercent !== null"
        class="progress-cursor"
        :style="{ left: cursorPercent + '%' }"
      ></div>
      <div
        v-if="hoverPercent !== null"
        class="progress-hover-tooltip"
        :style="{ left: hoverPercent + '%' }"
      >{{ formatAbsoluteTime(hoverAbsoluteTime) }}</div>
      <div
        class="progress-thumb"
        :style="{ left: playedPercent + '%' }"
      ></div>
    </div>
    <div class="controls-bar">
      <button class="control-btn" @click="togglePlay">
        <span v-if="isPlaying">❚❚</span>
        <span v-else>▶</span>
      </button>
      <div class="time-display">
        <span class="time-current">{{ formatTime(currentTime) }}</span>
        <span class="time-separator"> / </span>
        <span class="time-duration">{{ formatTime(duration) }}</span>
        <span class="time-absolute">
          ({{ formatAbsoluteTime(absoluteTime) }})
        </span>
      </div>
      <select
        class="speed-selector"
        :value="playSpeed"
        @change="onSpeedChange"
      >
        <option v-for="s in speeds" :key="s" :value="s">{{ s }}x</option>
      </select>
      <button class="control-btn" @click="toggleMute">
        <span v-if="muted">🔇</span>
        <span v-else>🔊</span>
      </button>
      <input
        type="range"
        class="volume-slider"
        min="0"
        max="1"
        step="0.05"
        :value="volume"
        @input="onVolumeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import Hls from 'hls.js'

const props = defineProps({
  src: {
    type: String,
    default: ''
  },
  startTime: {
    type: Number,
    default: 0
  },
  markers: {
    type: Array,
    default: () => []
  },
  cursorTime: {
    type: Number,
    default: null
  },
  autoPlay: {
    type: Boolean,
    default: false
  },
  playSpeed: {
    type: Number,
    default: 1.0
  },
  height: {
    type: Number,
    default: 300
  }
})

const emit = defineEmits([
  'timeUpdate',
  'play',
  'pause',
  'seeked',
  'loaded',
  'error',
  'dragStart',
  'dragEnd'
])

const speeds = [0.5, 1, 2, 4, 8]

const videoRef = ref(null)
const progressContainerRef = ref(null)
const currentTime = ref(0)
const duration = ref(0)
const bufferedEnd = ref(0)
const isPlaying = ref(false)
const isBuffering = ref(false)
const error = ref(false)
const errorMessage = ref('')
const muted = ref(false)
const volume = ref(1)
const isSeeking = ref(false)
const hoverPercent = ref(null)
const hoverMarker = ref(null)

let hlsInstance = null

const playedPercent = computed(() => {
  if (!duration.value) return 0
  return Math.min(100, Math.max(0, (currentTime.value / duration.value) * 100))
})

const bufferedPercent = computed(() => {
  if (!duration.value) return 0
  return Math.min(100, Math.max(0, (bufferedEnd.value / duration.value) * 100))
})

const absoluteTime = computed(() => {
  return props.startTime + currentTime.value * 1000
})

const cursorPercent = computed(() => {
  if (props.cursorTime === null || !duration.value || !props.startTime) return null
  const relSec = (props.cursorTime - props.startTime) / 1000
  if (relSec < 0 || relSec > duration.value) return null
  return (relSec / duration.value) * 100
})

const hoverAbsoluteTime = computed(() => {
  if (hoverPercent.value === null || !duration.value) return 0
  const relSec = (hoverPercent.value / 100) * duration.value
  return props.startTime + relSec * 1000
})

const visibleMarkers = computed(() => {
  if (!props.markers || !duration.value || !props.startTime) return []
  return props.markers
    .map(m => {
      const relSec = (m.timestamp - props.startTime) / 1000
      if (relSec < 0 || relSec > duration.value) return null
      return {
        ...m,
        percent: (relSec / duration.value) * 100
      }
    })
    .filter(Boolean)
})

function initHls() {
  if (!videoRef.value || !props.src) return
  destroyHls()
  error.value = false
  errorMessage.value = ''

  const isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent) ||
    (videoRef.value.canPlayType('application/vnd.apple.mpegurl'))

  if (isSafari && !Hls.isSupported()) {
    videoRef.value.src = props.src
    return
  }

  if (Hls.isSupported()) {
    hlsInstance = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
      backBufferLength: 90
    })
    hlsInstance.loadSource(props.src)
    hlsInstance.attachMedia(videoRef.value)
    hlsInstance.on(Hls.Events.ERROR, (_event, data) => {
      if (data.fatal) {
        switch (data.type) {
          case Hls.ErrorTypes.NETWORK_ERROR:
            hlsInstance.startLoad()
            break
          case Hls.ErrorTypes.MEDIA_ERROR:
            hlsInstance.recoverMediaError()
            break
          default:
            error.value = true
            errorMessage.value = '视频加载失败'
            emit('error', data)
            destroyHls()
        }
      }
    })
  } else if (!isSafari) {
    error.value = true
    errorMessage.value = '浏览器不支持HLS播放'
    emit('error', { type: 'not_supported' })
  }
}

function destroyHls() {
  if (hlsInstance) {
    hlsInstance.destroy()
    hlsInstance = null
  }
}

function retryLoad() {
  initHls()
}

function togglePlay() {
  if (!videoRef.value) return
  if (videoRef.value.paused) {
    videoRef.value.play().catch(() => {})
  } else {
    videoRef.value.pause()
  }
}

function onPlay() {
  isPlaying.value = true
  emit('play')
}

function onPause() {
  isPlaying.value = false
  emit('pause')
}

function onTimeUpdate() {
  if (!videoRef.value) return
  currentTime.value = videoRef.value.currentTime
  if (videoRef.value.buffered.length > 0) {
    bufferedEnd.value = videoRef.value.buffered.end(videoRef.value.buffered.length - 1)
  }
  emit('timeUpdate', {
    currentTime: currentTime.value,
    absoluteTime: absoluteTime.value
  })
}

function onLoadedMetadata() {
  if (!videoRef.value) return
  duration.value = videoRef.value.duration
  videoRef.value.playbackRate = props.playSpeed
  emit('loaded', { duration: duration.value })
  if (props.autoPlay) {
    videoRef.value.play().catch(() => {})
  }
}

function onWaiting() {
  isBuffering.value = true
}

function onPlaying() {
  isBuffering.value = false
}

function onError(e) {
  if (hlsInstance) return
  error.value = true
  errorMessage.value = '视频播放错误'
  emit('error', e)
}

function onSeeked() {
  emit('seeked', { currentTime: currentTime.value, absoluteTime: absoluteTime.value })
}

function onSpeedChange(e) {
  const speed = parseFloat(e.target.value)
  if (videoRef.value) {
    videoRef.value.playbackRate = speed
  }
}

function toggleMute() {
  if (!videoRef.value) return
  muted.value = !muted.value
  videoRef.value.muted = muted.value
}

function onVolumeChange(e) {
  const v = parseFloat(e.target.value)
  volume.value = v
  if (videoRef.value) {
    videoRef.value.volume = v
  }
  if (v > 0 && muted.value) {
    muted.value = false
    videoRef.value.muted = false
  }
}

function getProgressPercent(e) {
  if (!progressContainerRef.value) return 0
  const rect = progressContainerRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  return Math.min(100, Math.max(0, (x / rect.width) * 100))
}

function seekToPercent(percent) {
  if (!videoRef.value || !duration.value) return
  const newTime = (percent / 100) * duration.value
  videoRef.value.currentTime = newTime
  currentTime.value = newTime
}

function onProgressMouseDown(e) {
  isSeeking.value = true
  emit('dragStart')
  const percent = getProgressPercent(e)
  seekToPercent(percent)
  window.addEventListener('mousemove', onWindowMouseMove)
  window.addEventListener('mouseup', onWindowMouseUp)
}

function onWindowMouseMove(e) {
  if (!isSeeking.value) return
  const percent = getProgressPercent(e)
  seekToPercent(percent)
}

function onWindowMouseUp() {
  if (!isSeeking.value) return
  isSeeking.value = false
  emit('dragEnd')
  window.removeEventListener('mousemove', onWindowMouseMove)
  window.removeEventListener('mouseup', onWindowMouseUp)
}

function onProgressMouseMove(e) {
  hoverPercent.value = getProgressPercent(e)
}

function onProgressMouseLeave() {
  hoverPercent.value = null
}

function formatTime(seconds) {
  if (!isFinite(seconds) || seconds < 0) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function formatAbsoluteTime(ts) {
  if (!ts || !isFinite(ts)) return '--:--:--.---'
  const d = new Date(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  const ms = String(d.getMilliseconds()).padStart(3, '0')
  return `${hh}:${mm}:${ss}.${ms}`
}

watch(() => props.src, () => {
  nextTick(() => initHls())
})

watch(() => props.playSpeed, (newSpeed) => {
  if (videoRef.value) {
    videoRef.value.playbackRate = newSpeed
  }
})

watch(() => props.cursorTime, (newCursor) => {
  if (isSeeking.value || newCursor === null || !duration.value || !props.startTime) return
  const relSec = (newCursor - props.startTime) / 1000
  if (relSec < 0 || relSec > duration.value) return
  const diff = Math.abs(relSec - currentTime.value)
  if (diff > 0.1) {
    if (videoRef.value) {
      videoRef.value.currentTime = relSec
    }
    currentTime.value = relSec
  }
})

onMounted(() => {
  nextTick(() => {
    if (videoRef.value) {
      videoRef.value.volume = volume.value
    }
    initHls()
  })
})

onUnmounted(() => {
  destroyHls()
  window.removeEventListener('mousemove', onWindowMouseMove)
  window.removeEventListener('mouseup', onWindowMouseUp)
})
</script>

<style scoped>
.hls-video-player {
  position: relative;
  width: 100%;
  background: #0d0f1a;
  border: 1px solid #2a2d42;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 200px;
}

.video-element {
  flex: 1;
  width: 100%;
  height: 100%;
  background: #000;
  object-fit: contain;
  cursor: pointer;
  min-height: 0;
}

.buffering-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 56px;
  background: rgba(13, 15, 26, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  pointer-events: none;
}

.error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 56px;
  background: rgba(13, 15, 26, 0.95);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  z-index: 15;
}

.error-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(245, 108, 108, 0.15);
  color: #f56c6c;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #f56c6c;
}

.error-text {
  color: #cfd3dc;
  font-size: 13px;
}

.retry-btn {
  padding: 6px 18px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.retry-btn:hover {
  background: #66b1ff;
}

.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #2a2d42;
  border-top-color: #409eff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.progress-container {
  position: relative;
  height: 20px;
  padding: 8px 0;
  cursor: pointer;
  background: #12151f;
  flex-shrink: 0;
}

.progress-track {
  position: absolute;
  left: 12px;
  right: 12px;
  top: 50%;
  height: 4px;
  margin-top: -2px;
  background: #2a2d42;
  border-radius: 2px;
  overflow: hidden;
}

.progress-buffered {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: #3a3f5c;
  transition: width 0.15s;
}

.progress-played {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: linear-gradient(90deg, #409eff, #66b1ff);
  transition: width 0.05s;
}

.progress-marker {
  position: absolute;
  top: 2px;
  bottom: 2px;
  width: 3px;
  border-radius: 2px;
  z-index: 5;
  transform: translateX(-50%);
}

.marker-tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 6px;
  padding: 4px 8px;
  background: rgba(20, 23, 40, 0.98);
  border: 1px solid #3a3f5c;
  border-radius: 4px;
  color: #e0e3eb;
  font-size: 11px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 20;
}

.progress-cursor {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #ff4d4f;
  z-index: 6;
  transform: translateX(-50%);
  box-shadow: 0 0 6px rgba(255, 77, 79, 0.6);
}

.progress-cursor::after {
  content: '';
  position: absolute;
  top: -2px;
  left: 50%;
  transform: translateX(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
}

.progress-hover-tooltip {
  position: absolute;
  bottom: 100%;
  transform: translateX(-50%);
  margin-bottom: 4px;
  padding: 3px 6px;
  background: rgba(64, 158, 255, 0.9);
  border-radius: 3px;
  color: #fff;
  font-size: 10px;
  font-family: 'SF Mono', Consolas, monospace;
  white-space: nowrap;
  pointer-events: none;
  z-index: 15;
}

.progress-thumb {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  margin-top: -6px;
  background: #fff;
  border-radius: 50%;
  transform: translateX(-50%) scale(0);
  transition: transform 0.15s;
  z-index: 7;
  box-shadow: 0 0 4px rgba(0, 0, 0, 0.4);
}

.progress-container:hover .progress-thumb {
  transform: translateX(-50%) scale(1);
}

.controls-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  height: 36px;
  background: #12151f;
  border-top: 1px solid #2a2d42;
  flex-shrink: 0;
}

.control-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: transparent;
  border: none;
  color: #cfd3dc;
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
  padding: 0;
  flex-shrink: 0;
}

.control-btn:hover {
  background: #2a2d42;
  color: #409eff;
}

.time-display {
  display: flex;
  align-items: center;
  color: #8b8fa3;
  font-size: 11px;
  font-family: 'SF Mono', Consolas, monospace;
  flex-shrink: 0;
  gap: 0;
}

.time-current {
  color: #e0e3eb;
}

.time-absolute {
  margin-left: 8px;
  color: #409eff;
  font-size: 10px;
}

.speed-selector {
  background: #1a1d2e;
  border: 1px solid #2a2d42;
  color: #cfd3dc;
  padding: 3px 6px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  outline: none;
  flex-shrink: 0;
}

.speed-selector:hover {
  border-color: #3a3f5c;
}

.speed-selector option {
  background: #1a1d2e;
  color: #cfd3dc;
}

.volume-slider {
  width: 70px;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: #2a2d42;
  border-radius: 2px;
  outline: none;
  cursor: pointer;
  flex-shrink: 0;
}

.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 12px;
  height: 12px;
  background: #409eff;
  border-radius: 50%;
  cursor: pointer;
}

.volume-slider::-moz-range-thumb {
  width: 12px;
  height: 12px;
  background: #409eff;
  border-radius: 50%;
  cursor: pointer;
  border: none;
}
</style>
