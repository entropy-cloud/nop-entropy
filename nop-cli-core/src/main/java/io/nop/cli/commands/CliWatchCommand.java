/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli.commands;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.watch.FileChangeEvent;
import io.nop.core.resource.watch.FileWatcher;
import io.nop.core.resource.watch.NioFileWatchService;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import picocli.CommandLine;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "watch",
        mixinStandardHelpOptions = true,
        description = "监控指定目录或者文件的变化"
)
public class CliWatchCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0..*", description = "监控文件目录")
    String[] files;

    @CommandLine.Option(names = {"-p", "--patterns"},
            description = "监听的文件名模式")
    String[] fileNamePatterns;

    @CommandLine.Option(names = {"-e", "--execute"}, required = true,
            description = "发现文件变动后执行的代码文件路径")
    File executeFile;

    @CommandLine.Option(names = {"-w", "--wait"},
            description = "延迟处理等待间隔，缺省为100毫秒")
    int debounceWait = 100;

    @Override
    public Integer call() {
        NioFileWatchService watchService = new NioFileWatchService();
        FileWatcher watcher = new FileWatcher(watchService, GlobalExecutors.globalTimer());
        watcher.start();
        watcher.watch(Arrays.asList(files), fileNamePatterns == null ? null : Arrays.asList(fileNamePatterns),
                debounceWait, this::processEvents);
        watchService.stop();
        return 0;
    }

    private void processEvents(List<FileChangeEvent> events) {
        IResource resource = new FileResource(executeFile);
        XplModel xpl = XLang.parseXpl(resource, XLangOutputMode.none);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("changeEvents", events);
        xpl.invoke(scope);
    }
}