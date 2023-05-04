package io.nop.core.resource.watch;

import io.nop.api.core.util.ICancellable;
import io.nop.commons.concurrent.executor.IRateLimitExecutor;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.RateLimitExecutorImpl;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.StringHelper;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FileWatcher {
    private final NioFileWatchService watchService;
    private final BlockingQueue<FileChangeEvent> queue = new LinkedBlockingQueue<>();
    private final IScheduledExecutor executor;

    public FileWatcher(NioFileWatchService watchService, IScheduledExecutor executor) {
        this.watchService = watchService;
        this.executor = executor;

    }

    public void start() {
        this.watchService.start();
    }

    public void stop() {
        this.watchService.stop();
    }

    public ICancellable watch(List<String> paths, List<String> fileNamePatterns,
                              int debounce,
                              Consumer<List<FileChangeEvent>> handler) {

        List<Path> watchPaths = paths.stream().map(str -> new File(str).toPath()).collect(Collectors.toList());

        IRateLimitExecutor executor = new RateLimitExecutorImpl(this.executor);

        Cancellable cancellable = new Cancellable();
        for (Path watchPath : watchPaths) {
            Runnable cleanup = watchService.watch(watchPath, p -> matchPattern(p, fileNamePatterns), true, new IFileWatchListener() {
                @Override
                public void onFileChange(Path root, Path path) {
                    queue.add(new FileChangeEvent(FileChangeEvent.CHANGE_TYPE_MODIFY, path));
                    this.triggerChange(handler);
                }

                private void triggerChange(Consumer<List<FileChangeEvent>> handler) {
                    executor.debounce("process", debounce, () -> processChangeEvent(handler));
                }

                @Override
                public void onFileCreate(Path root, Path path) {
                    queue.add(new FileChangeEvent(FileChangeEvent.CHANGE_TYPE_ADD, path));
                    this.triggerChange(handler);
                }

                @Override
                public void onFileDelete(Path root, Path path) {
                    queue.add(new FileChangeEvent(FileChangeEvent.CHANGE_TYPE_DELETE, path));
                    this.triggerChange(handler);
                }
            });
            cancellable.appendOnCancelTask(cleanup);
        }
        return cancellable;
    }

    private boolean matchPattern(Path path, List<String> patterns) {
        if (patterns == null || patterns.isEmpty())
            return true;

        File file = path.toFile();
        if (file.isFile()) {
            for (String pattern : patterns) {
                if (StringHelper.matchSimplePattern(file.getName(), pattern))
                    return true;
            }
            return false;
        } else {
            return true;
        }
    }

    private void processChangeEvent(Consumer<List<FileChangeEvent>> handler) {
        List<FileChangeEvent> events = new ArrayList<>();
        do {
            events.clear();
            queue.drainTo(events);
            if (events.isEmpty())
                break;

            handler.accept(events);
        } while (true);
    }
}
