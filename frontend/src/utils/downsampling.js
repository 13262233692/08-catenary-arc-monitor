export function lttb(data, threshold) {
  const dataLength = data.length
  if (threshold >= dataLength || threshold === 0) {
    return data.slice()
  }

  const sampled = []
  sampled.push(data[0])

  const bucketSize = (dataLength - 2) / (threshold - 2)

  let a = 0

  for (let i = 0; i < threshold - 2; i++) {
    const avgRangeStart = Math.floor((i + 0) * bucketSize) + 1
    const avgRangeEnd = Math.floor((i + 1) * bucketSize) + 1
    const avgRangeLength = avgRangeEnd - avgRangeStart

    let avgX = 0
    let avgY = 0
    for (; avgRangeStart < avgRangeEnd; avgRangeStart++) {
      avgX += data[avgRangeStart].x
      avgY += data[avgRangeStart].y
    }
    avgX /= avgRangeLength
    avgY /= avgRangeLength

    const rangeOffs = Math.floor((i + 1) * bucketSize) + 1
    const rangeTo = Math.floor((i + 2) * bucketSize) + 1

    const pointAX = data[a].x
    const pointAY = data[a].y

    let maxArea = -1
    let maxIdx = rangeOffs

    for (; rangeOffs < rangeTo && rangeOffs < dataLength; rangeOffs++) {
      const area = Math.abs(
        (pointAX - avgX) * (data[rangeOffs].y - pointAY) -
        (pointAX - data[rangeOffs].x) * (avgY - pointAY)
      ) * 0.5
      if (area > maxArea) {
        maxArea = area
        maxIdx = rangeOffs
      }
    }

    sampled.push(data[maxIdx])
    a = maxIdx
  }

  sampled.push(data[dataLength - 1])

  return sampled
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
      if (data[j].y < minY) {
        minY = data[j].y
        minIdx = j
      }
      if (data[j].y > maxY) {
        maxY = data[j].y
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
