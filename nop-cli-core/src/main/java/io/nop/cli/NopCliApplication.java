/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli;

import io.nop.boot.NopApplication;
import io.nop.cli.commands.MainCommand;
import io.nop.cli.exception.NopExitCodeExceptionMapper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.quarkus.core.QuarkusIntegration;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import picocli.CommandLine;

@QuarkusMain
public class NopCliApplication implements QuarkusApplication {

    private CommandLine.IFactory factory;

    @Inject
    public void setFactory(CommandLine.IFactory factory){
        this.factory = factory;
    }

    @Override
    public int run(String... args) {
        if (args.length == 0) {
            new CommandLine(new MainCommand(), factory).usage(System.out);
            return 0;
        }
        QuarkusIntegration.start();
        CommandLine cmd = new CommandLine(new MainCommand(), factory);
        cmd.setExitCodeExceptionMapper(new NopExitCodeExceptionMapper());

        return new NopApplication().run(args, () -> cmd.execute(args));
    }

    public void stop(@Observes ShutdownEvent event) {
        CoreInitialization.destroy();
    }

    public static void main(String[] args) {
        //System.out.println(System.getProperty("file.encoding"));
        Quarkus.run(NopCliApplication.class, args);
        // System.exit(0);
    }
}