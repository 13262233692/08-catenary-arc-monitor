import { ref, onUnmounted } from 'vue'

export function useWebSocket(path = '/ws/arc', options = {}) {
  const {
    throttleMs = 1000 / 30,
    idleTimeout = 60000,
    onMessage = null
  } = options

  const connected = ref(false)
  const lastMessage = ref(null)
  const error = ref(null)
  const messageCount = ref(0)
  const latency = ref(0)

  let ws = null
  let reconnectTimer = null
  let reconnectAttempts = 0
  const maxReconnectAttempts = 10
  const baseReconnectDelay = 1000

  let throttleTimer = null
  let throttledMessage = null
  let hasThrottledMessage = false

  let idleTimer = null
  let pingSentTime = 0
  let latencyPingTimer = null
  const latencyPingInterval = 30000

  function getWsUrl() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}${path}`
  }

  function resetIdleTimer() {
    if (idleTimer) {
      clearTimeout(idleTimer)
    }
    idleTimer = setTimeout(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.close()
      }
    }, idleTimeout)
  }

  function flushThrottledMessage() {
    if (hasThrottledMessage) {
      lastMessage.value = throttledMessage
      hasThrottledMessage = false
    }
    throttleTimer = null
  }

  function handleMessage(msg) {
    messageCount.value++
    resetIdleTimer()

    if (msg && msg.type === 'ping') {
      send({ type: 'pong' })
      return
    }

    if (msg && msg.type === 'pong') {
      if (pingSentTime > 0) {
        latency.value = Date.now() - pingSentTime
        pingSentTime = 0
      }
      return
    }

    if (onMessage) {
      onMessage(msg)
    }

    if (throttleMs <= 0) {
      lastMessage.value = msg
      return
    }

    throttledMessage = msg
    hasThrottledMessage = true

    if (!throttleTimer) {
      throttleTimer = setTimeout(flushThrottledMessage, throttleMs)
    }
  }

  function sendLatencyPing() {
    if (ws && ws.readyState === WebSocket.OPEN) {
      pingSentTime = Date.now()
      send({ type: 'ping' })
    }
  }

  function startLatencyPing() {
    if (latencyPingTimer) {
      clearInterval(latencyPingTimer)
    }
    latencyPingTimer = setInterval(sendLatencyPing, latencyPingInterval)
    setTimeout(sendLatencyPing, 1000)
  }

  function stopLatencyPing() {
    if (latencyPingTimer) {
      clearInterval(latencyPingTimer)
      latencyPingTimer = null
    }
    pingSentTime = 0
  }

  function connect() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      return
    }

    const token = localStorage.getItem('token')
    const url = token ? `${getWsUrl()}?token=${token}` : getWsUrl()

    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value = true
      error.value = null
      reconnectAttempts = 0
      resetIdleTimer()
      startLatencyPing()
    }

    ws.onmessage = (event) => {
      let msg
      try {
        msg = JSON.parse(event.data)
      } catch {
        msg = event.data
      }
      handleMessage(msg)
    }

    ws.onerror = (e) => {
      error.value = e
    }

    ws.onclose = () => {
      connected.value = false
      stopLatencyPing()
      if (idleTimer) {
        clearTimeout(idleTimer)
        idleTimer = null
      }
      if (throttleTimer) {
        clearTimeout(throttleTimer)
        throttleTimer = null
      }
      scheduleReconnect()
    }
  }

  function scheduleReconnect() {
    if (reconnectAttempts >= maxReconnectAttempts) return
    const delay = baseReconnectDelay * Math.pow(2, reconnectAttempts)
    reconnectAttempts++
    reconnectTimer = setTimeout(() => {
      connect()
    }, Math.min(delay, 30000))
  }

  function reconnect() {
    disconnect()
    reconnectAttempts = 0
    connect()
  }

  function send(data) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(typeof data === 'string' ? data : JSON.stringify(data))
    }
  }

  function subscribe(sectionId) {
    send({ action: 'subscribe', sectionId })
  }

  function unsubscribe(sectionId) {
    send({ action: 'unsubscribe', sectionId })
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (idleTimer) {
      clearTimeout(idleTimer)
      idleTimer = null
    }
    if (throttleTimer) {
      clearTimeout(throttleTimer)
      throttleTimer = null
    }
    stopLatencyPing()
    reconnectAttempts = maxReconnectAttempts
    if (ws) {
      ws.close()
      ws = null
    }
    connected.value = false
    hasThrottledMessage = false
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    lastMessage,
    error,
    messageCount,
    latency,
    connect,
    disconnect,
    reconnect,
    send,
    subscribe,
    unsubscribe
  }
}
