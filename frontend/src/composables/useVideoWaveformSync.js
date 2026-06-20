import { ref, watch, computed, shallowRef } from 'vue'

export function useVideoWaveformSync() {
  const videoAbsoluteTime = ref(null)
  const chartCursorTime = ref(null)
  const isUserDraggingVideo = ref(false)
  const isUserDraggingChart = ref(false)
  const syncToleranceMs = ref(100)
  const cursorDriftMs = ref(0)

  const videoStartTimeEpoch = ref(null)
  const syncing = shallowRef(false)
  let lastDriftLogTs = 0

  const effectiveDiff = computed(() => {
    if (videoAbsoluteTime.value === null || videoAbsoluteTime.value === undefined) return Infinity
    if (chartCursorTime.value === null || chartCursorTime.value === undefined) return Infinity
    return Math.abs(videoAbsoluteTime.value - chartCursorTime.value)
  })

  function updateDrift() {
    const v = videoAbsoluteTime.value
    const c = chartCursorTime.value
    if (v !== null && v !== undefined && c !== null && c !== undefined) {
      cursorDriftMs.value = c - v
    }
  }

  function shouldEnforceSync() {
    return effectiveDiff.value > syncToleranceMs.value
  }

  function updateVideoTime(absoluteTime, fromUser = false) {
    if (syncing.value && !fromUser) return

    const prevTime = videoAbsoluteTime.value
    videoAbsoluteTime.value = absoluteTime
    updateDrift()

    if (isUserDraggingVideo.value || fromUser) {
      if (chartCursorTime.value !== absoluteTime) {
        syncing.value = true
        try {
          chartCursorTime.value = absoluteTime
        } finally {
          syncing.value = false
        }
      }
      return
    }

    if (!isUserDraggingChart.value && shouldEnforceSync()) {
      syncing.value = true
      try {
        chartCursorTime.value = absoluteTime
      } finally {
        syncing.value = false
      }
    }
  }

  function updateChartCursor(absoluteTime, fromUser = false) {
    if (syncing.value && !fromUser) return

    chartCursorTime.value = absoluteTime
    updateDrift()

    if (isUserDraggingChart.value || fromUser) {
      if (videoAbsoluteTime.value !== absoluteTime) {
        syncing.value = true
        try {
          videoAbsoluteTime.value = absoluteTime
        } finally {
          syncing.value = false
        }
      }
    }

    const now = Date.now()
    if (now - lastDriftLogTs > 5000) {
      lastDriftLogTs = now
    }
  }

  function startVideoSeek() {
    isUserDraggingVideo.value = true
  }

  function endVideoSeek() {
    isUserDraggingVideo.value = false
    if (videoAbsoluteTime.value !== null && videoAbsoluteTime.value !== undefined) {
      syncing.value = true
      try {
        chartCursorTime.value = videoAbsoluteTime.value
      } finally {
        syncing.value = false
      }
    }
  }

  function startChartSeek() {
    isUserDraggingChart.value = true
  }

  function endChartSeek() {
    isUserDraggingChart.value = false
    if (chartCursorTime.value !== null && chartCursorTime.value !== undefined) {
      syncing.value = true
      try {
        videoAbsoluteTime.value = chartCursorTime.value
      } finally {
        syncing.value = false
      }
    }
  }

  function setVideoStartTime(absoluteEpochMs) {
    videoStartTimeEpoch.value = absoluteEpochMs
  }

  function getVideoOffsetForAbsoluteTime(absTime) {
    if (videoStartTimeEpoch.value === null || videoStartTimeEpoch.value === undefined) {
      return 0
    }
    return Math.max(0, (absTime - videoStartTimeEpoch.value) / 1000)
  }

  function forceSync(direction) {
    syncing.value = true
    try {
      if (direction === 'chart-to-video') {
        if (chartCursorTime.value !== null && chartCursorTime.value !== undefined) {
          videoAbsoluteTime.value = chartCursorTime.value
          updateDrift()
        }
      } else if (direction === 'video-to-chart') {
        if (videoAbsoluteTime.value !== null && videoAbsoluteTime.value !== undefined) {
          chartCursorTime.value = videoAbsoluteTime.value
          updateDrift()
        }
      }
    } finally {
      syncing.value = false
    }
  }

  watch(videoAbsoluteTime, (newVal) => {
    if (syncing.value) return
    if (isUserDraggingChart.value) return

    if (newVal !== null && newVal !== undefined) {
      const diff = effectiveDiff.value
      if (diff > syncToleranceMs.value) {
        syncing.value = true
        try {
          chartCursorTime.value = newVal
          updateDrift()
        } finally {
          syncing.value = false
        }
      }
    }
  })

  watch(chartCursorTime, (newVal) => {
    if (syncing.value) return
    if (isUserDraggingVideo.value) return
    if (!isUserDraggingChart.value) return

    if (newVal !== null && newVal !== undefined) {
      syncing.value = true
      try {
        videoAbsoluteTime.value = newVal
        updateDrift()
      } finally {
        syncing.value = false
      }
    }
  })

  return {
    videoAbsoluteTime,
    chartCursorTime,
    isUserDraggingVideo,
    isUserDraggingChart,
    syncToleranceMs,
    cursorDriftMs,
    updateVideoTime,
    updateChartCursor,
    startVideoSeek,
    endVideoSeek,
    startChartSeek,
    endChartSeek,
    setVideoStartTime,
    getVideoOffsetForAbsoluteTime,
    forceSync
  }
}

export function formatMsToClock(ms) {
  if (ms === null || ms === undefined || isNaN(ms)) return '00:00:00.000'
  const neg = ms < 0
  const absMs = Math.abs(ms)
  const totalSec = Math.floor(absMs / 1000)
  const hh = Math.floor(totalSec / 3600).toString().padStart(2, '0')
  const mm = Math.floor((totalSec % 3600) / 60).toString().padStart(2, '0')
  const ss = (totalSec % 60).toString().padStart(2, '0')
  const msPart = Math.floor(absMs % 1000).toString().padStart(3, '0')
  return `${neg ? '-' : ''}${hh}:${mm}:${ss}.${msPart}`
}

export function formatEpochToClock(epochMs) {
  if (epochMs === null || epochMs === undefined || isNaN(epochMs)) return '--:--:--.---'
  const d = new Date(epochMs)
  const hh = d.getHours().toString().padStart(2, '0')
  const mm = d.getMinutes().toString().padStart(2, '0')
  const ss = d.getSeconds().toString().padStart(2, '0')
  const ms = d.getMilliseconds().toString().padStart(3, '0')
  return `${hh}:${mm}:${ss}.${ms}`
}

export function parseClockToMs(str) {
  if (!str || typeof str !== 'string') return null
  const clean = str.trim()
  const neg = clean.startsWith('-')
  const numStr = neg ? clean.slice(1) : clean
  const parts = numStr.split(':')
  let h = 0, m = 0, s = 0, ms = 0
  if (parts.length === 3) {
    h = parseInt(parts[0], 10) || 0
    m = parseInt(parts[1], 10) || 0
    const sPart = parts[2].split('.')
    s = parseInt(sPart[0], 10) || 0
    ms = sPart[1] ? parseInt((sPart[1] + '000').slice(0, 3), 10) : 0
  } else if (parts.length === 2) {
    m = parseInt(parts[0], 10) || 0
    const sPart = parts[1].split('.')
    s = parseInt(sPart[0], 10) || 0
    ms = sPart[1] ? parseInt((sPart[1] + '000').slice(0, 3), 10) : 0
  } else if (parts.length === 1) {
    const sPart = parts[0].split('.')
    s = parseInt(sPart[0], 10) || 0
    ms = sPart[1] ? parseInt((sPart[1] + '000').slice(0, 3), 10) : 0
  } else {
    return null
  }
  const total = (h * 3600 + m * 60 + s) * 1000 + ms
  return neg ? -total : total
}
