/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.source.file;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.source.ConfigSourceHelper;
import io.nop.config.source.IConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractFileConfigSource implements IConfigSource {
    static final Logger LOG = LoggerFactory.getLogger(AbstractFileConfigSource.class);

    /**
     * 监控这些目录下的子文件
     */
    private final List<Path> paths;
    private final Future<?> refreshFuture;
    private volatile Map<String, ValueWithLocation> vars; //NOSONAR

    private final List<Runnable> tasks = new CopyOnWriteArrayList<>();

    public AbstractFileConfigSource(Collection<String> paths, long refreshInterval) {
        this.paths = paths.stream().map(Paths::get).collect(Collectors.toList());
        if (refreshInterval > 0) {
            this.refreshFuture = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::refreshConfig,
                    refreshInterval, refreshInterval, TimeUnit.MILLISECONDS);
        } else {
            refreshFuture = null;
        }
        this.vars = loadConfig();
    }

    private void refreshConfig() {
        Map<String, ValueWithLocation> vars = loadConfig();
        if (ConfigSourceHelper.isChanged(this.vars, vars)) {
            for (Runnable task : tasks) {
                task.run();
            }
        }
    }

    protected Map<String, ValueWithLocation> loadConfig() {
        try {
            List<Path> filePaths = this.paths.stream().filter(f -> {
                if (!Files.exists(f)) {
                    LOG.warn("nop.config.ignore-not-exists-path:path={}", f);
                    return false;
                }
                return true;
            }).flatMap(path -> {
                try {
                    if (Files.isRegularFile(path))
                        return Stream.of(path);
                    return Files.walk(path).filter(Files::isRegularFile);
                } catch (Exception e) {
                    return Stream.empty();
                }
            }).sorted(Path::compareTo).collect(Collectors.toList());
            return loadConfigFromPath(filePaths);
        } catch (Exception e) {
            LOG.error("nop.config.load-file-source-fail:paths={}", paths, e);
            throw NopException.adapt(e);
        }
    }

    protected abstract Map<String, ValueWithLocation> loadConfigFromPath(List<Path> paths);

    @Override
    public Map<String, ValueWithLocation> getConfigValues() {
        return vars;
    }

    @Override
    public void addOnChange(Runnable callback) {
        tasks.add(callback);
    }

    @Override
    public void close() {
        if (refreshFuture != null) {
            refreshFuture.cancel(false);
        }
    }
}