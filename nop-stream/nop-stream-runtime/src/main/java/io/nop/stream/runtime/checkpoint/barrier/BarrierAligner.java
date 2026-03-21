/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.barrier;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class BarrierAligner {

    private final int numberOfInputs;
    private final List<TreeMap<Long, CheckpointBarrier>> inputBarriers;
    private final Queue<AlignedBarrier> alignedBarriers = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean closed = false;

    public BarrierAligner(int numberOfInputs) {
        this.numberOfInputs = numberOfInputs;
        this.inputBarriers = new ArrayList<>(numberOfInputs);
        for (int i = 0; i < numberOfInputs; i++) {
            inputBarriers.add(new TreeMap<>());
        }
    }

    public boolean processBarrier(CheckpointBarrier barrier, int inputIndex) {
        if (closed) {
            return false;
        }
        lock.lock();
        try {
            TreeMap<Long, CheckpointBarrier> barriers = inputBarriers.get(inputIndex);
            long checkpointId = barrier.getId();
            if (barriers.containsKey(checkpointId)) {
                return false;
            }
            barriers.put(checkpointId, barrier);
            return checkComplete();
        } finally {
            lock.unlock();
        }
    }

    private boolean checkComplete() {
        if (numberOfInputs == 0) {
            return false;
        }
        
        Long completedCheckpointId = findCompletedCheckpointId();
        if (completedCheckpointId == null) {
            return false;
        }
        
        CheckpointBarrier firstBarrier = inputBarriers.get(0).get(completedCheckpointId);
        CheckpointType checkpointType = firstBarrier.getCheckpointType();
        long triggerTimestamp = firstBarrier.getTimestamp();
        long alignmentTime = System.currentTimeMillis();
        
        AlignedBarrier aligned = new AlignedBarrier(
                completedCheckpointId,
                checkpointType,
                triggerTimestamp,
                alignmentTime,
                numberOfInputs
        );
        alignedBarriers.offer(aligned);
        
        for (TreeMap<Long, CheckpointBarrier> barriers : inputBarriers) {
            barriers.remove(completedCheckpointId);
        }
        
        return true;
    }
    
    private Long findCompletedCheckpointId() {
        Map<Long, Integer> checkpointCounts = new HashMap<>();
        for (TreeMap<Long, CheckpointBarrier> barriers : inputBarriers) {
            for (Long checkpointId : barriers.keySet()) {
                checkpointCounts.merge(checkpointId, 1, Integer::sum);
            }
        }
        
        Long minCompleteCheckpoint = null;
        for (Map.Entry<Long, Integer> entry : checkpointCounts.entrySet()) {
            if (entry.getValue() == numberOfInputs) {
                if (minCompleteCheckpoint == null || entry.getKey() < minCompleteCheckpoint) {
                    minCompleteCheckpoint = entry.getKey();
                }
            }
        }
        return minCompleteCheckpoint;
    }

    public boolean hasPendingBarriers() {
        lock.lock();
        try {
            for (TreeMap<Long, CheckpointBarrier> barriers : inputBarriers) {
                if (!barriers.isEmpty()) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public int getPendingBarrierCount() {
        lock.lock();
        try {
            int count = 0;
            for (TreeMap<Long, CheckpointBarrier> barriers : inputBarriers) {
                count += barriers.size();
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    public AlignedBarrier pollAlignedBarrier() {
        lock.lock();
        try {
            return alignedBarriers.poll();
        } finally {
            lock.unlock();
        }
    }

    public AlignedBarrier pollAlignedBarrier(long timeout, TimeUnit unit) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        lock.lock();
        try {
            while (alignedBarriers.isEmpty()) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= unit.toMillis(timeout)) {
                    return null;
                }
                lock.unlock();
                Thread.sleep(10);
                lock.lock();
            }
            return alignedBarriers.poll();
        } finally {
            lock.unlock();
        }
    }

    public void abortAll() {
        lock.lock();
        try {
            for (TreeMap<Long, CheckpointBarrier> barriers : inputBarriers) {
                barriers.clear();
            }
            alignedBarriers.clear();
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            closed = true;
            for (TreeMap<Long, CheckpointBarrier> barriers : inputBarriers) {
                barriers.clear();
            }
            alignedBarriers.clear();
        } finally {
            lock.unlock();
        }
    }

    public long getCurrentCheckpointId() {
        lock.lock();
        try {
            long maxId = -1L;
            for (TreeMap<Long, CheckpointBarrier> barriers : inputBarriers) {
                if (!barriers.isEmpty()) {
                    Long lastKey = barriers.lastKey();
                    if (lastKey > maxId) {
                        maxId = lastKey;
                    }
                }
            }
            return maxId;
        } finally {
            lock.unlock();
        }
    }

    public int getNumberOfInputs() {
        return numberOfInputs;
    }
}
