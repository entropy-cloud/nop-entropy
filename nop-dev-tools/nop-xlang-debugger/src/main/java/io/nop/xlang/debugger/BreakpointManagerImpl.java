/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.IBreakpointManager;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.collections.IntHashMap;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.nop.commons.cache.CacheConfig.newConfig;

/**
 * 管理所有XLang断点
 */
public class BreakpointManagerImpl implements IBreakpointManager {
    private static final Logger LOG = LoggerFactory.getLogger(BreakpointManagerImpl.class);

    // key为externalPath而不是SourceLocation.getPath()。因为外部编辑器增加断点的时候可能只知道文件本身的绝对路径，而不是在虚拟文件系统中的虚拟路径
    // plan 2479 C-9（item 24 审计）：表引用 volatile + 独立锁整表原子替换——并发
    // setBreakpoints 后写覆盖（旧形态 clear+逐条 add 产生并集）；读侧单引用一致快照，
    // getBreakpointAt 引擎热路径无锁（替换后见旧表或新表，均为完整意图）
    private volatile Map<String, IntHashMap<Breakpoint>> breakpoints = new ConcurrentHashMap<>();
    private final Object bpLock = new Object();

    private boolean useExternalPath = true;

    public void setUseExternalPath(boolean useExternalPath) {
        this.useExternalPath = useExternalPath;
    }

    @Override
    public void setBreakpoints(List<Breakpoint> bps) {
        Map<String, IntHashMap<Breakpoint>> fresh = new ConcurrentHashMap<>();
        if (bps != null) {
            for (Breakpoint bp : bps) {
                IntHashMap<Breakpoint> map =
                        fresh.computeIfAbsent(bp.getSourcePath(), path -> new IntHashMap<>());
                synchronized (map) {
                    map.put(bp.getLine(), bp);
                }
            }
        }
        synchronized (bpLock) {
            this.breakpoints = fresh;
        }
        LOG.info("nop.debugger.set-breakpoints:count={}", bps == null ? 0 : bps.size());
    }

    @Override
    public void addBreakpoint(Breakpoint bp) {
        LOG.info("nop.debugger.add-breakpoint:bp={}", bp);
        synchronized (bpLock) {
            IntHashMap<Breakpoint> map =
                    breakpoints.computeIfAbsent(bp.getSourcePath(), path -> new IntHashMap<>());
            synchronized (map) {
                map.put(bp.getLine(), bp);
            }
        }
    }

    @Override
    public void removeBreakpoint(Breakpoint bp) {
        LOG.info("nop.debugger.remove-breakpoint:bp={}", bp);
        synchronized (bpLock) {
            IntHashMap<Breakpoint> map = breakpoints.get(bp.getSourcePath());
            if (map != null) {
                synchronized (map) {
                    map.remove(bp.getLine());
                }
            }
        }
    }

    @Override
    public List<Breakpoint> getBreakpoints() {
        synchronized (bpLock) {
            return breakpoints.values().stream().flatMap(map -> {
                synchronized (map) { //NOSONAR
                    return map.values().toArray().stream();
                }
            }).collect(Collectors.toList());
        }
    }

    @Override
    public void clearBreakpoints() {
        LOG.info("nop.debugger.clear-breakpoints");
        synchronized (bpLock) {
            this.breakpoints = new ConcurrentHashMap<>();
        }
    }

    @Override
    public Breakpoint getBreakpointAt(SourceLocation loc) {
        // 使用cellPath而不是path
        String path = toSourcePath(loc);
        IntHashMap<Breakpoint> map = breakpoints.get(path);
        if (map == null) {
            return null;
        }

        synchronized (map) {
            return map.get(loc.getLine());
        }
    }

    private final ICache<String, String> externalPathCache = LocalCache.newCache("external-path-cache", newConfig(1000),
            path -> {
                if (!VirtualFileSystem.isInitialized())
                    return path;

                if (!path.startsWith("file:") && !ResourceHelper.isNormalVirtualPath(path))
                    return path;

                IResource res = VirtualFileSystem.instance().getRawResource(path, true);
                String externalPath = null;
                if (res != null) {
                    externalPath = res.getExternalPath();
                }
                if (externalPath == null)
                    externalPath = path;
                return externalPath;
            });

    protected String toSourcePath(SourceLocation loc) {
        if (!useExternalPath)
            return loc.getCellPath();

        String externalPath = loc.getExternalPath();
        if (externalPath != null)
            return externalPath;

        String path = loc.getPath();
        if (StringHelper.hasNamespace(path)) {
            externalPath = path;
        } else {
            externalPath = externalPathCache.get(path);
        }
        loc.setExternalPath(externalPath);
        return externalPath;
    }
}
