export function lttb(data, threshold) {
  const dataLength = data.length
  if (threshold >= dataLength || threshold === 0) {
    return data.slice()
  }

  const sampled = new Array(threshold)
  sampled[0] = data[0]

  const bucketSize = (dataLength - 2) / (threshold - 2)

  let a = 0
  let sampledIndex = 1

  for (let i = 0; i < threshold - 2; i++) {
    const avgRangeStart = Math.floor((i + 0) * bucketSize) + 1
    const avgRangeEnd = Math.floor((i + 1) * bucketSize) + 1
    const avgRangeLength = avgRangeEnd - avgRangeStart

    let avgX = 0
    let avgY = 0
    let j = avgRangeStart
    for (; j < avgRangeEnd; j++) {
      const point = data[j]
      avgX += point.x
      avgY += point.y
    }
    avgX /= avgRangeLength
    avgY /= avgRangeLength

    const rangeOffs = Math.floor((i + 1) * bucketSize) + 1
    const rangeTo = Math.floor((i + 2) * bucketSize) + 1

    const pointA = data[a]
    const pointAX = pointA.x
    const pointAY = pointA.y

    let maxArea = -1
    let maxIdx = rangeOffs

    const pointAXMinusAvgX = pointAX - avgX
    const pointAYMinusAvgY = pointAY - avgY

    for (let k = rangeOffs; k < rangeTo && k < dataLength; k++) {
      const point = data[k]
      const area = Math.abs(
        pointAXMinusAvgX * (point.y - pointAY) -
        (pointAX - point.x) * pointAYMinusAvgY
      ) * 0.5
      if (area > maxArea) {
        maxArea = area
        maxIdx = k
      }
    }

    sampled[sampledIndex++] = data[maxIdx]
    a = maxIdx
  }

  sampled[threshold - 1] = data[dataLength - 1]

  return sampled
}

export function streamingLttb(data, threshold, keepLastN = 100) {
  const dataLength = data.length
  if (dataLength <= threshold) {
    return data.slice()
  }

  const tailCount = Math.min(keepLastN, dataLength)
  const headCount = dataLength - tailCount

  if (headCount <= 0) {
    return data.slice()
  }

  const headThreshold = threshold - tailCount

  if (headThreshold <= 2) {
    const result = new Array(threshold)
    const step = headCount / (threshold - tailCount)
    let idx = 0
    for (let i = 0; i < threshold - tailCount; i++) {
      result[idx++] = data[Math.floor(i * step)]
    }
    for (let i = headCount; i < dataLength; i++) {
      result[idx++] = data[i]
    }
    return result
  }

  const headData = lttb(data.slice(0, headCount), headThreshold)
  const tailData = data.slice(headCount)

  const result = new Array(headData.length + tailData.length)
  for (let i = 0; i < headData.length; i++) {
    result[i] = headData[i]
  }
  for (let i = 0; i < tailData.length; i++) {
    result[headData.length + i] = tailData[i]
  }

  return result
}

export function createStreamingLttb(threshold, keepLastN = 100) {
  const buffer = []
  const sampledHistorical = []
  const tailBuffer = []
  const headThreshold = Math.max(2, threshold - keepLastN)

  let historicalCount = 0
  let downsampleAccumulator = 0

  function rebuild() {
    const allData = new Array(historicalCount + buffer.length)
    for (let i = 0; i < historicalCount; i++) {
      allData[i] = sampledHistorical[i]
    }
    for (let i = 0; i < buffer.length; i++) {
      allData[historicalCount + i] = buffer[i]
    }

    if (allData.length <= threshold) {
      return allData.slice()
    }

    const tailCount = Math.min(keepLastN, allData.length)
    const headCount = allData.length - tailCount

    if (headCount <= headThreshold) {
      return lttb(allData, threshold)
    }

    return streamingLttb(allData, threshold, keepLastN)
  }

  function append(points) {
    if (!points || points.length === 0) return

    for (let i = 0; i < points.length; i++) {
      buffer.push(points[i])
    }

    if (buffer.length > keepLastN * 4) {
      const allData = new Array(historicalCount + buffer.length)
      for (let i = 0; i < historicalCount; i++) {
        allData[i] = sampledHistorical[i]
      }
      for (let i = 0; i < buffer.length; i++) {
        allData[historicalCount + i] = buffer[i]
      }

      const tailCount = Math.min(keepLastN, allData.length)
      const headCount = allData.length - tailCount

      if (headCount > headThreshold) {
        const headData = allData.slice(0, headCount)
        const downsampled = lttb(headData, headThreshold)
        sampledHistorical.length = 0
        for (let i = 0; i < downsampled.length; i++) {
          sampledHistorical.push(downsampled[i])
        }
        historicalCount = sampledHistorical.length

        buffer.length = 0
        for (let i = headCount; i < allData.length; i++) {
          buffer.push(allData[i])
        }
      }
    }
  }

  function getSampled() {
    return rebuild()
  }

  function clear() {
    buffer.length = 0
    sampledHistorical.length = 0
    tailBuffer.length = 0
    historicalCount = 0
    downsampleAccumulator = 0
  }

  return {
    append,
    getSampled,
    clear
  }
}

export function minMaxDownsample(data, threshold) {
  if (data.length <= threshold) {
    return data.slice()
  }

  const bucketSize = data.length / threshold
  const sampled = []

  for (let i = 0; i < threshold; i++) {
    const start = Math.floor(i * bucketSize)
    const end = Math.floor((i + 1) * bucketSize)
    let minY = Infinity
    let maxY = -Infinity
    let minIdx = start
    let maxIdx = start

    for (let j = start; j < end && j < data.length; j++) {
      const point = data[j]
      const y = point.y
      if (y < minY) {
        minY = y
        minIdx = j
      }
      if (y > maxY) {
        maxY = y
        maxIdx = j
      }
    }

    if (minIdx < maxIdx) {
      sampled.push(data[minIdx])
      sampled.push(data[maxIdx])
    } else if (minIdx > maxIdx) {
      sampled.push(data[maxIdx])
      sampled.push(data[minIdx])
    } else {
      sampled.push(data[minIdx])
    }
  }

  return sampled
}
