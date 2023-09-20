/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.boot;

import io.nop.commons.service.ShutdownHook;
import io.nop.core.CoreConfigs;
import io.nop.core.command.ApplicationArguments;
import io.nop.core.command.ExecCommandProcessor;
import io.nop.core.command.ICommandExecutor;
import io.nop.core.command.args.SimpleCommandLineArgsParser;
import io.nop.core.initialize.CoreInitialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.IntSupplier;

public class NopApplication {
    static final Logger LOG = LoggerFactory.getLogger(NopApplication.class);

    private String bannerPath = NopBootConstants.DEFAULT_BANNER_PATH;

    public void setBannerPath(String bannerPath) {
        this.bannerPath = bannerPath;
    }

    public int run(String[] args, IntSupplier task) {
        StartupInfoLogger infoLogger = new StartupInfoLogger(getClass());
        infoLogger.logStarting(LOG);

        ApplicationArguments.set(new SimpleCommandLineArgsParser().parse(args));

        ShutdownHook.getInstance().addDisposable(() -> {
            CoreInitialization.destroy();
        });

        CoreInitialization.initialize();

        if (NopBootConfigs.CFG_BANNER_ENABLED.get())
            new NopBanner(bannerPath).print();

        infoLogger.logStarted(LOG);

        if (args.length > 0 && CoreConfigs.CFG_CORE_NOP_COMMAND_EXECUTOR_ENABLED.get()) {
            if (args[0].equals(ICommandExecutor.NOP_EXEC_COMMAND)) {
                try {
                    new ExecCommandProcessor(true).process(ApplicationArguments.get());
                    return 0;
                } finally {
                    CoreInitialization.destroy();
                }
            }
        }

        int ret = task.getAsInt();

        return ret;
    }

    public int run(String[] args) {
        return run(args, () -> 0);
    }
}
