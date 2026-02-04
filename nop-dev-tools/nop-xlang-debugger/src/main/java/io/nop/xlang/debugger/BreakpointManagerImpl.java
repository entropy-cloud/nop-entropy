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
    private final Map<String, IntHashMap<Breakpoint>> breakpoints = new ConcurrentHashMap<>();

    private boolean useExternalPath = true;

    public void setUseExternalPath(boolean useExternalPath) {
        this.useExternalPath = useExternalPath;
    }

    @Override
    public void setBreakpoints(List<Breakpoint> bps) {
        clearBreakpoints();
        if (bps != null) {
            for (Breakpoint bp : bps) {
                addBreakpoint(bp);
            }
        }
    }

    @Override
    public void addBreakpoint(Breakpoint bp) {
        LOG.info("nop.debugger.add-breakpoint:bp={}", bp);
        IntHashMap<Breakpoint> map = breakpoints.computeIfAbsent(bp.getSourcePath(), path -> new IntHashMap<>());
        synchronized (map) {
            map.put(bp.getLine(), bp);
        }
    }

    @Override
    public void removeBreakpoint(Breakpoint bp) {
        LOG.info("nop.debugger.remove-breakpoint:bp={}", bp);
        IntHashMap<Breakpoint> map = breakpoints.get(bp.getSourcePath());
        if (map != null) {
            synchronized (map) {
                map.remove(bp.getLine());
            }
        }
    }

    @Override
    public List<Breakpoint> getBreakpoints() {
        return breakpoints.values().stream().flatMap(map -> {
            synchronized (map) { //NOSONAR
                return map.values().toArray().stream();
            }
        }).collect(Collectors.toList());
    }

    @Override
    public void clearBreakpoints() {
        LOG.info("nop.debugger.clear-breakpoints");
        breakpoints.clear();
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
