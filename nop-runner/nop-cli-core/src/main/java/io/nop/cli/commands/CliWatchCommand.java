/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.watch.FileChangeEvent;
import io.nop.core.resource.watch.FileWatcher;
import io.nop.core.resource.watch.NioFileWatchService;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.nop.cli.CliErrors.ARG_NAME;
import static io.nop.cli.CliErrors.ARG_PATH;
import static io.nop.cli.CliErrors.ERR_CLI_UNKNOWN_SCRIPT;

@CommandLine.Command(name = "watch", mixinStandardHelpOptions = true, description = "Watch specified directories or files for changes and execute a script")
public class CliWatchCommand implements Callable<Integer> {
    static final Logger LOG = LoggerFactory.getLogger(CliWatchCommand.class);

    @CommandLine.Parameters(index = "0", description = "Directories to watch", arity = "1..10")
    File[] watchDirs;

    @CommandLine.Option(names = {"-p", "--patterns"}, description = "File name patterns to watch (e.g. *.meta.xml). If omitted, any change triggers events")
    String[] fileNamePatterns;

    @CommandLine.Option(names = {"-e", "--execute"}, required = true, description = "Script file to execute when changes are detected")
    String scriptFile;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output directory (default: current directory)")
    File outputDir;

    @CommandLine.Option(names = {"-w", "--wait"}, description = "Debounce wait interval in milliseconds (default: 100)")
    int debounceWait = 100;

    @CommandLine.Option(names = {"-i", "--input"}, description = "Input parameters (JSON)")
    String input;

    @Inject
    FileWatcherFactory fileWatcherFactory;

    /**
     * P1-4.5-R2: the jdk11 launcher and NopCliApplication's {@code factory == null}
     * branch do not perform container injection, so {@link #fileWatcherFactory} is
     * null there. Fall back to a locally owned watch service instead of NPEing.
     * The injected instance is owned by the container and must NOT be stopped here;
     * only a locally created one is stopped by the caller.
     */
    static final class WatcherHandle {
        final FileWatcher watcher;
        final boolean locallyOwned;

        WatcherHandle(FileWatcher watcher, boolean locallyOwned) {
            this.watcher = watcher;
            this.locallyOwned = locallyOwned;
        }
    }

    WatcherHandle newFileWatcher() {
        if (fileWatcherFactory != null) {
            return new WatcherHandle(fileWatcherFactory.newFileWatcher(), false);
        }
        NioFileWatchService service = new NioFileWatchService();
        service.start();
        return new WatcherHandle(new FileWatcher(service, GlobalExecutors.globalTimer()), true);
    }

    @Override
    public Integer call() {
        WatcherHandle handle = newFileWatcher();
        FileWatcher watcher = handle.watcher;
        ICancellable registration = null;
        try {
            registration = doWatch(watcher);
            return 0;
        } finally {
            // MA-02: the watch() registration must be unwatched on every exit path. cancel()
            // removes only this invocation's keys (NioFileWatchService.unwatch is idempotent),
            // so the injected (shared) watch service stays alive; a locally owned service is
            // additionally stopped below. Null guard: watch() not reached when the script is
            // missing or the first run throws (R2-m4).
            if (registration != null) {
                registration.cancel();
            }
            if (handle.locallyOwned) {
                // only a locally created watch service is owned by this invocation
                watcher.stop();
            }
        }
    }

    private ICancellable doWatch(FileWatcher watcher) {
        Map<String, Object> state = new ConcurrentHashMap<>();

        if (outputDir == null) outputDir = FileHelper.currentDir();

        LOG.info("nop.cli.watch:dir={},outputDir={}", Arrays.asList(watchDirs), outputDir);

        IResource resource = ResourceHelper.resolveRelativePathResource(scriptFile);
        if (!resource.exists()) {
            throw new NopException(ERR_CLI_UNKNOWN_SCRIPT).param(ARG_NAME, scriptFile).param(ARG_PATH, resource.getPath());
        }

        XplModel xpl = XLang.parseXpl(resource, XLangOutputMode.none);

    // Always execute script once before starting watch loop
        processEvents(xpl, Collections.emptyList(), state);

        ICancellable registration =
                watcher.watch(Arrays.asList(watchDirs).stream().map(File::getAbsolutePath).collect(Collectors.toList()),
                        fileNamePatterns == null ? null : Arrays.asList(fileNamePatterns),
                        debounceWait, events -> processEvents(xpl, events, state));

        try {
            System.in.read();
        } catch (IOException e) {
            // EOF (e.g. stdin closed) is a normal way to stop watching; log it for diagnosis.
            LOG.debug("nop.cli.watch: input read ended, stopping watch", e);
        }

        return registration;
    }

    /**
     * Package-private: test seam (plan 2563 I-C4-002 / R1-M6). A script failure is logged at
     * ERROR level and swallowed so the watch loop survives and later event batches are still
     * processed (aligned with CliWatchZipCommand's catch+LOG.error shape; failure is visible,
     * unlike the previous silent bubble into FileWatcher.triggerChange).
     */
    void processEvents(XplModel xpl, Collection<FileChangeEvent> events, Map<String, Object> state) {
        IEvalScope scope = XLang.newEvalScope();

        if (input != null) {
            Map<String, Object> map = (Map<String, Object>) JsonTool.parseNonStrict(null, input);
            scope.setLocalValues(map);
        }

        scope.setLocalValue("changeEvents", events);
        scope.setLocalValue("globalState", state);
        scope.setLocalValue("watchDirs", watchDirs);
        scope.setLocalValue("outputDir", outputDir);
        try {
            xpl.invoke(scope);
        } catch (Exception e) {
            LOG.error("nop.cli.watch.process-events-failed", e);
        }
    }
}