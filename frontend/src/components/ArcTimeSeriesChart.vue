<template>
  <div class="arc-time-series-chart-wrapper">
    <div ref="chartRef" class="arc-time-series-chart" :style="{ height: height + 'px' }"></div>
    <div v-if="!connected" class="reconnect-badge">RECONNECTING</div>
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { lttb, createStreamingLttb } from '@/utils/downsampling'
import { useDataQuery } from '@/composables/useDataQuery'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  height: {
    type: Number,
    default: 300
  },
  title: {
    type: String,
    default: ''
  },
  warningThreshold: {
    type: Number,
    default: 80
  },
  alarmThreshold: {
    type: Number,
    default: 120
  },
  maxDisplayPoints: {
    type: Number,
    default: 800
  },
  liveMode: {
    type: Boolean,
    default: true
  },
  timeRangeMs: {
    type: Number,
    default: 300000
  },
  connected: {
    type: Boolean,
    default: true
  },
  sectionId: {
    type: [String, Number],
    default: null
  },
  queryFn: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['data-point-click', 'range-change'])

const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null
let rafId = null
let lastFlushTime = 0
const FLUSH_INTERVAL = 33

const rawData = ref([])
const displayData = ref([])
const chartRenderData = []
const dataBuffer = []
let flushCounter = 0
const DOWNSAMPLE_EVERY_N_FLUSHES = 10

let streamingLttb = null

const { data: queryData, loading, query, refetch } = useDataQuery(
  async (params, signal) => {
    if (!props.queryFn) return []
    return await props.queryFn(params, signal)
  },
  { debounceMs: 200 }
)

function initStreamingLttb() {
  streamingLttb = createStreamingLttb(props.maxDisplayPoints)
}

function applySlidingWindow(data) {
  if (!data || data.length === 0) return []
  const latestTs = data[data.length - 1].timestamp
  const cutoff = latestTs - props.timeRangeMs
  let left = 0
  let right = data.length - 1
  while (left <= right) {
    const mid = Math.floor((left + right) / 2)
    if (data[mid].timestamp < cutoff) {
      left = mid + 1
    } else {
      right = mid - 1
    }
  }
  return data.slice(left)
}

function downsampleForDisplay(data) {
  if (!data || data.length <= props.maxDisplayPoints) {
    return data.map(d => [d.timestamp, d.intensity])
  }
  const points = data.map(d => ({ x: d.timestamp, y: d.intensity }))
  const sampled = lttb(points, props.maxDisplayPoints)
  return sampled.map(p => [p.x, p.y])
}

function flushBuffer() {
  if (dataBuffer.length === 0) return

  const newPoints = dataBuffer.splice(0, dataBuffer.length)
  rawData.value = applySlidingWindow([...rawData.value, ...newPoints])

  if (streamingLttb) {
    const streamPoints = newPoints.map(d => ({ x: d.timestamp, y: d.intensity }))
    streamingLttb.append(streamPoints)
  }

  flushCounter++

  if (flushCounter >= DOWNSAMPLE_EVERY_N_FLUSHES) {
    flushCounter = 0
    displayData.value = downsampleForDisplay(rawData.value)
    chartRenderData.length = 0
    chartRenderData.push(...displayData.value)
    updateChartFull()
  } else {
    appendChartData(newPoints)
  }
}

function scheduleFlush() {
  if (rafId) return

  const now = performance.now()
  const timeSinceLastFlush = now - lastFlushTime

  if (timeSinceLastFlush >= FLUSH_INTERVAL) {
    flushBuffer()
    lastFlushTime = now
    rafId = null
  } else {
    rafId = requestAnimationFrame(() => {
      flushBuffer()
      lastFlushTime = performance.now()
      rafId = null
    })
  }
}

function appendChartData(points) {
  if (!chartInstance || points.length === 0) return

  const chartPoints = points.map(d => [d.timestamp, d.intensity])
  chartRenderData.push(...chartPoints)

  if (chartRenderData.length > props.maxDisplayPoints * 2) {
    const overflow = chartRenderData.length - props.maxDisplayPoints
    chartRenderData.splice(0, overflow)
  }

  chartInstance.setOption({
    series: [{
      data: chartRenderData
    }]
  }, {
    notMerge: false,
    lazyUpdate: true,
    silent: true
  })
}

function updateChartFull() {
  if (!chartInstance) return

  chartInstance.setOption({
    series: [{
      data: displayData.value
    }]
  }, {
    notMerge: false,
    lazyUpdate: true,
    silent: true
  })
}

function initChart() {
  if (!chartRef.value) return

  chartInstance = echarts.init(chartRef.value, 'dark')

  chartInstance.setOption({
    backgroundColor: 'transparent',
    animation: false,
    title: {
      text: props.title,
      textStyle: { color: '#cfd3dc', fontSize: 14, fontWeight: 600 },
      left: 10,
      top: 5
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(20, 23, 40, 0.95)',
      borderColor: '#3a3f5c',
      borderWidth: 1,
      textStyle: { color: '#e0e3eb', fontSize: 12 },
      animation: false,
      transitionDuration: 0,
      formatter(params) {
        const p = params[0]
        if (!p) return ''
        const date = new Date(p.value[0])
        const time = date.toLocaleTimeString('zh-CN', { hour12: false })
        const ms = date.getMilliseconds().toString().padStart(3, '0')
        const rawPoint = findRawPoint(p.value[0])
        const intensity = rawPoint ? rawPoint.intensity : p.value[1]
        return `${time}.${ms}<br/>放电强度: <b style="color:#409eff">${intensity?.toFixed(3)}</b> W`
      }
    },
    grid: {
      left: 60,
      right: 20,
      top: props.title ? 40 : 20,
      bottom: 40
    },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: '#3a3f5c' } },
      axisLabel: {
        color: '#8b8fa3',
        fontSize: 11,
        formatter: '{HH}:{mm}:{ss}'
      },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      name: '放电强度 (W)',
      nameTextStyle: { color: '#8b8fa3', fontSize: 11 },
      axisLine: { lineStyle: { color: '#3a3f5c' } },
      axisLabel: { color: '#8b8fa3', fontSize: 11 },
      splitLine: { lineStyle: { color: '#2a2d42', type: 'dashed' } }
    },
    dataZoom: [
      {
        type: 'inside',
        xAxisIndex: 0,
        filterMode: 'filter',
        zoomOnMouseWheel: true,
        moveOnMouseMove: true,
        moveOnMouseWheel: false
      },
      {
        type: 'slider',
        xAxisIndex: 0,
        filterMode: 'filter',
        height: 20,
        bottom: 5,
        borderColor: '#2a2d42',
        backgroundColor: '#1a1d2e',
        fillerColor: 'rgba(64,158,255,0.15)',
        handleStyle: { color: '#409eff' },
        textStyle: { color: '#8b8fa3' },
        dataBackground: {
          lineStyle: { color: '#409eff' },
          areaStyle: { color: 'rgba(64,158,255,0.1)' }
        }
      }
    ],
    series: [
      {
        type: 'line',
        data: displayData.value,
        smooth: false,
        symbol: 'none',
        animation: false,
        large: true,
        largeThreshold: 500,
        sampling: 'lttb',
        progressive: 5000,
        progressiveThreshold: 10000,
        lineStyle: { color: '#409eff', width: 1.5 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.25)' },
            { offset: 1, color: 'rgba(64,158,255,0.02)' }
          ])
        },
        emphasis: {
          disabled: true
        },
        markLine: {
          silent: true,
          symbol: 'none',
          animation: false,
          data: [
            {
              yAxis: props.warningThreshold,
              lineStyle: { color: '#e6a23c', type: 'dashed', width: 1.5 },
              label: {
                formatter: '预警线 ' + props.warningThreshold + 'W',
                color: '#e6a23c',
                fontSize: 11,
                position: 'insideEndTop'
              }
            },
            {
              yAxis: props.alarmThreshold,
              lineStyle: { color: '#f56c6c', type: 'dashed', width: 1.5 },
              label: {
                formatter: '报警线 ' + props.alarmThreshold + 'W',
                color: '#f56c6c',
                fontSize: 11,
                position: 'insideEndTop'
              }
            }
          ]
        }
      }
    ]
  }, true)

  chartInstance.on('click', (params) => {
    if (params.componentType === 'series') {
      const rawPoint = findRawPoint(params.value[0])
      emit('data-point-click', rawPoint || { timestamp: params.value[0], intensity: params.value[1] })
    }
  })

  chartInstance.on('dataZoom', () => {
    const option = chartInstance.getOption()
    const dataZoom = option.dataZoom[0]
    if (dataZoom && dataZoom.startValue !== undefined && dataZoom.endValue !== undefined) {
      emit('range-change', {
        start: dataZoom.startValue,
        end: dataZoom.endValue
      })
    }
  })
}

function findRawPoint(timestamp) {
  if (!rawData.value || rawData.value.length === 0) return null
  let left = 0
  let right = rawData.value.length - 1
  while (left <= right) {
    const mid = Math.floor((left + right) / 2)
    if (rawData.value[mid].timestamp === timestamp) {
      return rawData.value[mid]
    } else if (rawData.value[mid].timestamp < timestamp) {
      left = mid + 1
    } else {
      right = mid - 1
    }
  }
  if (right < 0) return rawData.value[0]
  if (left >= rawData.value.length) return rawData.value[rawData.value.length - 1]
  const leftDiff = timestamp - rawData.value[right].timestamp
  const rightDiff = rawData.value[left].timestamp - timestamp
  return leftDiff <= rightDiff ? rawData.value[right] : rawData.value[left]
}

function handleResize() {
  chartInstance?.resize()
}

watch(() => props.data, (newData) => {
  if (!newData || newData.length === 0) return

  if (props.liveMode) {
    const lastRawTs = rawData.value.length > 0 ? rawData.value[rawData.value.length - 1].timestamp : 0
    const newPoints = newData.filter(d => d.timestamp > lastRawTs)
    if (newPoints.length > 0) {
      dataBuffer.push(...newPoints)
      scheduleFlush()
    }
  } else {
    rawData.value = applySlidingWindow([...newData])
    displayData.value = downsampleForDisplay(rawData.value)
    chartRenderData.length = 0
    chartRenderData.push(...displayData.value)
    nextTick(updateChartFull)
  }
}, { deep: true })

watch(() => props.title, () => {
  if (!chartInstance) return
  chartInstance.setOption({
    title: {
      text: props.title
    }
  }, { notMerge: false, lazyUpdate: true })
})

watch(() => props.maxDisplayPoints, () => {
  initStreamingLttb()
  if (rawData.value.length > 0) {
    const points = rawData.value.map(d => ({ x: d.timestamp, y: d.intensity }))
    streamingLttb.append(points)
  }
  displayData.value = downsampleForDisplay(rawData.value)
  chartRenderData.length = 0
  chartRenderData.push(...displayData.value)
  nextTick(updateChartFull)
})

watch(() => props.warningThreshold, () => {
  if (!chartInstance) return
  chartInstance.setOption({
    series: [{
      markLine: {
        data: [
          {
            yAxis: props.warningThreshold,
            lineStyle: { color: '#e6a23c', type: 'dashed', width: 1.5 },
            label: {
              formatter: '预警线 ' + props.warningThreshold + 'W',
              color: '#e6a23c',
              fontSize: 11,
              position: 'insideEndTop'
            }
          },
          {
            yAxis: props.alarmThreshold,
            lineStyle: { color: '#f56c6c', type: 'dashed', width: 1.5 },
            label: {
              formatter: '报警线 ' + props.alarmThreshold + 'W',
              color: '#f56c6c',
              fontSize: 11,
              position: 'insideEndTop'
            }
          }
        ]
      }
    }]
  }, { notMerge: false, lazyUpdate: true })
})

watch(() => props.alarmThreshold, () => {
  if (!chartInstance) return
  chartInstance.setOption({
    series: [{
      markLine: {
        data: [
          {
            yAxis: props.warningThreshold,
            lineStyle: { color: '#e6a23c', type: 'dashed', width: 1.5 },
            label: {
              formatter: '预警线 ' + props.warningThreshold + 'W',
              color: '#e6a23c',
              fontSize: 11,
              position: 'insideEndTop'
            }
          },
          {
            yAxis: props.alarmThreshold,
            lineStyle: { color: '#f56c6c', type: 'dashed', width: 1.5 },
            label: {
              formatter: '报警线 ' + props.alarmThreshold + 'W',
              color: '#f56c6c',
              fontSize: 11,
              position: 'insideEndTop'
            }
          }
        ]
      }
    }]
  }, { notMerge: false, lazyUpdate: true })
})

watch(queryData, (newData) => {
  if (newData && Array.isArray(newData)) {
    rawData.value = applySlidingWindow([...newData])
    displayData.value = downsampleForDisplay(rawData.value)
    chartRenderData.length = 0
    chartRenderData.push(...displayData.value)
    nextTick(updateChartFull)
  }
})

watch(() => [props.sectionId, props.timeRangeMs], () => {
  if (props.sectionId && props.queryFn) {
    const end = Date.now()
    const start = end - props.timeRangeMs
    query({
      sectionId: props.sectionId,
      startTime: start,
      endTime: end
    })
  }
}, { immediate: false })

onMounted(() => {
  initStreamingLttb()

  if (props.data && props.data.length > 0) {
    rawData.value = applySlidingWindow([...props.data])
    displayData.value = downsampleForDisplay(rawData.value)
    chartRenderData.length = 0
    chartRenderData.push(...displayData.value)
    const points = rawData.value.map(d => ({ x: d.timestamp, y: d.intensity }))
    streamingLttb.append(points)
  }

  nextTick(() => {
    initChart()
    resizeObserver = new ResizeObserver(() => {
      handleResize()
    })
    if (chartRef.value) {
      resizeObserver.observe(chartRef.value)
    }
  })
})

onUnmounted(() => {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  dataBuffer.length = 0
  chartRenderData.length = 0
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.arc-time-series-chart-wrapper {
  position: relative;
  width: 100%;
  min-height: 200px;
}

.arc-time-series-chart {
  width: 100%;
  min-height: 200px;
}

.reconnect-badge {
  position: absolute;
  top: 10px;
  right: 20px;
  padding: 4px 12px;
  background: rgba(230, 162, 60, 0.9);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  letter-spacing: 0.5px;
  animation: pulse 1.5s ease-in-out infinite;
  pointer-events: none;
  z-index: 10;
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.7;
  }
  50% {
    opacity: 1;
  }
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(20, 23, 40, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #2a2d42;
  border-top-color: #409eff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
