<template>
  <div ref="chartRef" class="arc-time-series-chart" :style="{ height: height + 'px' }"></div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { lttb } from '@/utils/downsampling'

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
  }
})

const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null

function downsampleData(data) {
  if (!data || data.length <= props.maxDisplayPoints) return data
  const points = data.map(d => ({ x: d.timestamp, y: d.intensity }))
  const sampled = lttb(points, props.maxDisplayPoints)
  return sampled.map(p => {
    const original = data.find(d => d.timestamp === p.x)
    return original || { timestamp: p.x, intensity: p.y }
  })
}

function buildChartData(data) {
  const processed = downsampleData(data)
  return processed.map(d => [d.timestamp, d.intensity])
}

function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value, 'dark')
  updateChart()
}

function updateChart() {
  if (!chartInstance) return

  const chartData = buildChartData(props.data)

  chartInstance.setOption({
    backgroundColor: 'transparent',
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
      formatter(params) {
        const p = params[0]
        if (!p) return ''
        const date = new Date(p.value[0])
        const time = date.toLocaleTimeString('zh-CN', { hour12: false })
        const ms = date.getMilliseconds().toString().padStart(3, '0')
        return `${time}.${ms}<br/>放电强度: <b style="color:#409eff">${p.value[1]?.toFixed(3)}</b> W`
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
        filterMode: 'filter'
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
        data: chartData,
        smooth: false,
        symbol: 'none',
        animation: false,
        lineStyle: { color: '#409eff', width: 1.5 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.25)' },
            { offset: 1, color: 'rgba(64,158,255,0.02)' }
          ])
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
}

function handleResize() {
  chartInstance?.resize()
}

watch(() => props.data, () => {
  nextTick(updateChart)
}, { deep: true })

watch(() => props.title, () => {
  nextTick(updateChart)
})

onMounted(() => {
  nextTick(initChart)
  resizeObserver = new ResizeObserver(() => {
    handleResize()
  })
  if (chartRef.value) {
    resizeObserver.observe(chartRef.value)
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.arc-time-series-chart {
  width: 100%;
  min-height: 200px;
}
</style>
