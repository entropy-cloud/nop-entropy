/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.watch;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.ExecutorHelper;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.IoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class NioFileWatchService extends LifeCycleSupport implements IFileWatchService {

    static final Logger LOG = LoggerFactory.getLogger(NioFileWatchService.class);
    private final Map<WatchKey, FileWatchEntry> watchKeyMap = new HashMap<>();
    private ExecutorService executor;
    private Future<?> future;
    private WatchService watchService;
    private boolean followLinks;
    private int maxDepth = Integer.MAX_VALUE;

    public NioFileWatchService() {

    }

    public void setFollowLinks(boolean followLinks) {
        this.followLinks = followLinks;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    protected void doStart() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        executor = ExecutorHelper.newSingleThreadExecutor("nio-file-watch-service", 1, false);
        future = executor.submit(this::checkChange);
    }

    private void checkChange() {
        try {
            WatchService watchService = this.getWatchService();

            do {
                try {
                    WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                    if (key == null)
                        continue;

                    FileWatchEntry entry = watchKeyMap.get(key);
                    if (entry == null || !key.isValid()) {
                        LOG.trace("nop.core.resource.watch.key-invalid:{}", key);
                        continue;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path eventPath = (Path) event.context();
                        eventPath = entry.getPath().resolve(eventPath);

                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            LOG.debug("nop.core.resource.watch.create-file:{}", eventPath);

                            if (entry.accept(eventPath)) {
                                try {
                                    entry.getListener().onFileCreate(entry.getRoot(), eventPath);
                                } catch (Exception e) {
                                    LOG.error("nop.core.resource.watch.invoke-watch-listener-fail", e);
                                }
                            }

                            if (entry.isRecursive() && Files.isDirectory(eventPath)) {
                                FileWatchEntry subEntry = register(entry.getRoot(), eventPath, entry.getFilter(),
                                        entry.isRecursive(), entry.getListener());
                                entry.addSubEntry(subEntry);
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            LOG.trace("nop.core.resource.watch.modify-file:{}", eventPath);

                            if (entry.accept(eventPath)) {
                                try {
                                    entry.getListener().onFileChange(entry.getRoot(), eventPath);
                                } catch (Exception e) {
                                    LOG.error("nop.core.resource.watch.invoke-watch-listener-fail", e);
                                }
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            LOG.debug("nop.core.resource.watch.delete-file:{}", eventPath);

                            if (entry.accept(eventPath)) {
                                try {
                                    entry.getListener().onFileDelete(entry.getRoot(), eventPath);
                                } catch (Exception e) {
                                    LOG.error("nop.core.resource.watch.invoke-watch-listener-fail", e);
                                }
                            }

                            FileWatchEntry subEntry = entry.getSubEntry(eventPath);
                            if (subEntry != null) {
                                unwatch(subEntry);
                            }
                        } else {
                            LOG.debug("nop.core.resource.watch.unhandled-event:{}", event.kind());
                        }
                    }
                    key.reset();
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                    throw NopException.adapt(e2);
                }
            } while (isActive());
        } catch (IOException e) {
            LOG.error("nop.core.resource.watch.file-watcher-fail", e);
        }
    }

    FileWatchEntry register(Path root, Path path, Predicate<Path> filter, boolean recursive,
                            IFileWatchListener listener) throws IOException {

        path = path.toAbsolutePath();
        WatchKey key = path.register(getWatchService(), StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

        FileWatchEntry entry = new FileWatchEntry(key, root, path, filter, recursive, listener);
        watchKeyMap.put(key, entry);
        LOG.debug("nop.core.resource.watch.register-watch:{}", path);
        return entry;
    }

    WatchService getWatchService() {
        return watchService;
    }

    public synchronized Runnable watch(final Path path, final Predicate<Path> filter, boolean recursive,
                                       final IFileWatchListener listener) {
        try {
            FileWatchEntry entry = register(path, path, filter, recursive, listener);
            if (recursive) {
                Files.walkFileTree(path,
                        followLinks ? EnumSet.of(FileVisitOption.FOLLOW_LINKS) : EnumSet.noneOf(FileVisitOption.class),
                        maxDepth, new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                                    throws IOException {
                                if (filter != null && !filter.test(dir))
                                    return FileVisitResult.SKIP_SUBTREE;

                                if (!path.equals(dir)) {
                                    FileWatchEntry subEntry = register(path, dir, filter, true, listener);
                                    getParentEntry(entry, dir.getParent()).addSubEntry(subEntry);
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
            }

            return () -> unwatch(entry);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    private FileWatchEntry getParentEntry(FileWatchEntry parentEntry, Path path) {
        Path root = parentEntry.getRoot();
        if (root.equals(path)) {
            return parentEntry;
        }

        FileWatchEntry parent = getParentEntry(parentEntry, path.getParent());
        return parent;
    }

    @Override
    protected synchronized void doStop() {
        if (future != null)
            future.cancel(true);

        for (WatchKey watchKey : watchKeyMap.keySet()) {
            watchKey.cancel();
        }
        watchKeyMap.clear();

        if (executor != null)
            executor.shutdown();
        if (watchService != null) {
            IoHelper.safeClose(watchService);
            watchService = null;
        }
    }

    private void unwatch(FileWatchEntry entry) {
        LOG.debug("nop.core.resource.cancel-watch:{}", entry.getPath());
        this.watchKeyMap.remove(entry.getWatchKey());
        entry.getWatchKey().cancel();

        for (FileWatchEntry subEntry : entry.getSubEntries().values()) {
            unwatch(subEntry);
        }
    }
}