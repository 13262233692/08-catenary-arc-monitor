import { ref, onUnmounted } from 'vue'

export function useDataQuery(fetchFn, options = {}) {
  const {
    debounceMs = 200,
    cacheTtl = 5000,
    retries = 3
  } = options

  const data = ref(null)
  const loading = ref(false)
  const error = ref(null)

  let debounceTimer = null
  let lastParams = null
  let currentAbortController = null
  const pendingRequests = new Map()
  const cache = new Map()

  function hashParams(params) {
    return JSON.stringify(params)
  }

  function isTransientError(err) {
    if (!err) return false
    if (err.name === 'AbortError' || err.code === 'ECONNABORTED') {
      return false
    }
    const status = err.status || err.response?.status
    if (status && (status >= 500 || status === 408 || status === 429)) {
      return true
    }
    if (!err.response && err.message && err.message.includes('Network')) {
      return true
    }
    return false
  }

  async function fetchWithRetry(params, signal) {
    let lastError = null
    for (let attempt = 0; attempt <= retries; attempt++) {
      if (signal && signal.aborted) {
        const err = new Error('Aborted')
        err.name = 'AbortError'
        throw err
      }
      try {
        const result = await fetchFn(params, signal)
        return result
      } catch (err) {
        lastError = err
        if (err.name === 'AbortError' || signal?.aborted) {
          throw err
        }
        if (attempt < retries && isTransientError(err)) {
          const delay = Math.min(1000 * Math.pow(2, attempt), 10000)
          await new Promise(resolve => setTimeout(resolve, delay))
          continue
        }
        throw err
      }
    }
    throw lastError
  }

  function getCached(key) {
    const entry = cache.get(key)
    if (!entry) return null
    if (Date.now() - entry.timestamp > cacheTtl) {
      cache.delete(key)
      return null
    }
    return entry.data
  }

  function setCache(key, value) {
    cache.set(key, { data: value, timestamp: Date.now() })
  }

  async function executeQuery(params) {
    const key = hashParams(params)
    lastParams = params

    const cached = getCached(key)
    if (cached !== null) {
      data.value = cached
      error.value = null
      return cached
    }

    const pending = pendingRequests.get(key)
    if (pending) {
      return pending
    }

    if (currentAbortController) {
      currentAbortController.abort()
    }

    const abortController = new AbortController()
    currentAbortController = abortController

    loading.value = true
    error.value = null

    const promise = fetchWithRetry(params, abortController.signal)
      .then((result) => {
        if (abortController.signal.aborted) {
          const err = new Error('Aborted')
          err.name = 'AbortError'
          throw err
        }
        data.value = result
        error.value = null
        setCache(key, result)
        return result
      })
      .catch((err) => {
        if (err.name !== 'AbortError') {
          error.value = err
          data.value = null
        }
        throw err
      })
      .finally(() => {
        pendingRequests.delete(key)
        if (currentAbortController === abortController) {
          currentAbortController = null
          loading.value = false
        }
      })

    pendingRequests.set(key, promise)
    return promise
  }

  function query(params) {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
    }
    if (debounceMs <= 0) {
      executeQuery(params)
      return
    }
    debounceTimer = setTimeout(() => {
      executeQuery(params)
    }, debounceMs)
  }

  function refetch() {
    if (lastParams !== null) {
      executeQuery(lastParams)
    }
  }

  function cancel() {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
    if (currentAbortController) {
      currentAbortController.abort()
      currentAbortController = null
    }
  }

  function clearCache() {
    cache.clear()
    pendingRequests.clear()
  }

  onUnmounted(() => {
    cancel()
    clearCache()
  })

  return {
    data,
    loading,
    error,
    query,
    refetch,
    cancel,
    clearCache
  }
}
