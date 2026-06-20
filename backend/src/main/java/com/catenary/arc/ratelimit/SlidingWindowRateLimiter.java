package com.catenary.arc.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class SlidingWindowRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentLinkedDeque<Long> timestamps;
    private final AtomicInteger size;
    private final ReentrantLock lock;

    public SlidingWindowRateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
        this.timestamps = new ConcurrentLinkedDeque<>();
        this.size = new AtomicInteger(0);
        this.lock = new ReentrantLock();
    }

    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(int permits) {
        if (permits <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        lock.lock();
        try {
            evictExpired(now);
            int currentSize = size.get();
            if (currentSize + permits <= maxRequests) {
                for (int i = 0; i < permits; i++) {
                    timestamps.addLast(now);
                }
                size.addAndGet(permits);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public long getRemainingRequests() {
        evictExpired(System.currentTimeMillis());
        return Math.max(0, maxRequests - size.get());
    }

    public long getWindowResetTime() {
        long now = System.currentTimeMillis();
        evictExpired(now);
        Long oldest = timestamps.peekFirst();
        if (oldest == null) {
            return 0;
        }
        return Math.max(0, (oldest + windowMillis) - now);
    }

    private void evictExpired(long now) {
        long cutoff = now - windowMillis;
        int evicted = 0;
        while (true) {
            Long oldest = timestamps.peekFirst();
            if (oldest == null || oldest > cutoff) {
                break;
            }
            timestamps.pollFirst();
            evicted++;
        }
        if (evicted > 0) {
            size.addAndGet(-evicted);
        }
    }
}
