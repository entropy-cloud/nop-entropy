package io.nop.chart.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chart resource manager for memory and resource cleanup
 */
public class ChartResourceManager {
    private static final Logger LOG = LoggerFactory.getLogger(ChartResourceManager.class);
    
    private static final ChartResourceManager INSTANCE = new ChartResourceManager();
    
    // 资源跟踪
    private final ConcurrentMap<String, WeakReference<Object>> resources = new ConcurrentHashMap<>();
    private final AtomicLong resourceCounter = new AtomicLong(0);
    
    // 内存使用统计
    private final AtomicLong totalMemoryUsed = new AtomicLong(0);
    private final AtomicLong maxMemoryUsed = new AtomicLong(0);
    
    private ChartResourceManager() {
        // 私有构造函数
    }
    
    public static ChartResourceManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册资源
     * @param resource 资源对象
     * @return 资源ID
     */
    public String registerResource(Object resource) {
        String resourceId = "resource_" + resourceCounter.incrementAndGet();
        resources.put(resourceId, new WeakReference<>(resource));
        
        // 估算内存使用
        long memorySize = estimateMemorySize(resource);
        totalMemoryUsed.addAndGet(memorySize);
        
        long currentMemory = totalMemoryUsed.get();
        if (currentMemory > maxMemoryUsed.get()) {
            maxMemoryUsed.set(currentMemory);
        }
        
        LOG.debug("Registered resource: {}, estimated size: {} bytes", resourceId, memorySize);
        return resourceId;
    }
    
    /**
     * 释放资源
     * @param resourceId 资源ID
     */
    public void releaseResource(String resourceId) {
        WeakReference<Object> ref = resources.remove(resourceId);
        if (ref != null) {
            Object resource = ref.get();
            if (resource != null) {
                long memorySize = estimateMemorySize(resource);
                totalMemoryUsed.addAndGet(-memorySize);
                
                // 清理特定类型的资源
                cleanupResource(resource);
                
                LOG.debug("Released resource: {}, freed size: {} bytes", resourceId, memorySize);
            }
        }
    }
    
    /**
     * 清理所有资源
     */
    public void cleanupAllResources() {
        LOG.info("Cleaning up all resources, total count: {}", resources.size());
        
        for (String resourceId : resources.keySet()) {
            releaseResource(resourceId);
        }
        
        resources.clear();
        totalMemoryUsed.set(0);
        
        // 强制垃圾回收
        System.gc();
        
        LOG.info("All resources cleaned up");
    }
    
    /**
     * 清理过期的弱引用
     */
    public void cleanupExpiredReferences() {
        int cleanedCount = 0;
        
        for (String resourceId : resources.keySet()) {
            WeakReference<Object> ref = resources.get(resourceId);
            if (ref != null && ref.get() == null) {
                resources.remove(resourceId);
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            LOG.debug("Cleaned up {} expired resource references", cleanedCount);
        }
    }
    
    /**
     * 获取内存使用统计
     * @return 内存使用信息
     */
    public MemoryUsageInfo getMemoryUsageInfo() {
        cleanupExpiredReferences();
        
        return new MemoryUsageInfo(
            totalMemoryUsed.get(),
            maxMemoryUsed.get(),
            resources.size()
        );
    }
    
    /**
     * 检查内存使用是否超过阈值
     * @param maxMemoryBytes 最大内存字节数
     * @return true如果超过阈值
     */
    public boolean isMemoryUsageExceeded(long maxMemoryBytes) {
        return totalMemoryUsed.get() > maxMemoryBytes;
    }
    
    private long estimateMemorySize(Object resource) {
        if (resource instanceof BufferedImage) {
            BufferedImage image = (BufferedImage) resource;
            return (long) image.getWidth() * image.getHeight() * 4; // 假设ARGB格式
        } else if (resource instanceof Graphics2D) {
            return 1024; // 估算Graphics2D对象大小
        } else if (resource instanceof Font) {
            return 512; // 估算Font对象大小
        } else {
            return 256; // 默认估算大小
        }
    }
    
    private void cleanupResource(Object resource) {
        try {
            if (resource instanceof Graphics2D) {
                ((Graphics2D) resource).dispose();
            }
            // 其他资源类型的清理可以在这里添加
        } catch (Exception e) {
            LOG.warn("Failed to cleanup resource: {}", resource.getClass().getSimpleName(), e);
        }
    }
    
    /**
     * 内存使用信息
     */
    public static class MemoryUsageInfo {
        private final long currentMemoryUsed;
        private final long maxMemoryUsed;
        private final int activeResourceCount;
        
        public MemoryUsageInfo(long currentMemoryUsed, long maxMemoryUsed, int activeResourceCount) {
            this.currentMemoryUsed = currentMemoryUsed;
            this.maxMemoryUsed = maxMemoryUsed;
            this.activeResourceCount = activeResourceCount;
        }
        
        public long getCurrentMemoryUsed() {
            return currentMemoryUsed;
        }
        
        public long getMaxMemoryUsed() {
            return maxMemoryUsed;
        }
        
        public int getActiveResourceCount() {
            return activeResourceCount;
        }
        
        @Override
        public String toString() {
            return String.format("MemoryUsage{current=%d bytes, max=%d bytes, resources=%d}", 
                currentMemoryUsed, maxMemoryUsed, activeResourceCount);
        }
    }
}