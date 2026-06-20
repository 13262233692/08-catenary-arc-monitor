export class RequestQueue {
  constructor(options = {}) {
    this.maxConcurrency = options.maxConcurrency || 2
    this.maxQueueSize = options.maxQueueSize || 10
    this.queue = []
    this.activeCount = 0
  }

  add(promiseFactory) {
    return new Promise((resolve, reject) => {
      if (this.activeCount < this.maxConcurrency) {
        this.execute(promiseFactory, resolve, reject)
      } else {
        if (this.queue.length >= this.maxQueueSize) {
          const dropped = this.queue.shift()
          dropped.reject(new Error('Queue full'))
        }
        this.queue.push({ promiseFactory, resolve, reject })
      }
    })
  }

  execute(promiseFactory, resolve, reject) {
    this.activeCount++
    let promise
    try {
      promise = promiseFactory()
    } catch (err) {
      this.activeCount--
      reject(err)
      this.processNext()
      return
    }
    Promise.resolve(promise)
      .then(resolve)
      .catch(reject)
      .finally(() => {
        this.activeCount--
        this.processNext()
      })
  }

  processNext() {
    if (this.queue.length === 0 || this.activeCount >= this.maxConcurrency) {
      return
    }
    const next = this.queue.shift()
    this.execute(next.promiseFactory, next.resolve, next.reject)
  }

  cancelAll() {
    while (this.queue.length > 0) {
      const item = this.queue.shift()
      item.reject(new Error('Cancelled'))
    }
  }
}
