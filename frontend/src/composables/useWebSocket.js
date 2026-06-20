import { ref, onUnmounted } from 'vue'

export function useWebSocket(path = '/ws/arc') {
  const connected = ref(false)
  const lastMessage = ref(null)
  const error = ref(null)
  let ws = null
  let reconnectTimer = null
  let reconnectAttempts = 0
  const maxReconnectAttempts = 10
  const baseReconnectDelay = 1000

  function getWsUrl() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}${path}`
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
    }

    ws.onmessage = (event) => {
      try {
        lastMessage.value = JSON.parse(event.data)
      } catch {
        lastMessage.value = event.data
      }
    }

    ws.onerror = (e) => {
      error.value = e
    }

    ws.onclose = () => {
      connected.value = false
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
    reconnectAttempts = maxReconnectAttempts
    if (ws) {
      ws.close()
      ws = null
    }
    connected.value = false
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    lastMessage,
    error,
    connect,
    disconnect,
    send,
    subscribe,
    unsubscribe
  }
}
