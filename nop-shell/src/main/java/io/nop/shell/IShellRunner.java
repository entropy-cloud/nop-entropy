/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.shell;

public interface IShellRunner {
    int run(final ShellCommand command, final IShellOutputCollector collector);

    default ShellResult run(ShellCommand command) {
        DefaultShellOutputCollector collector = new DefaultShellOutputCollector();
        int retCode = run(command, collector);
        ShellResult result = new ShellResult();
        result.setReturnCode(retCode);
        result.setError(collector.getError());
        result.setOutput(collector.getOutput());
        return result;
    }
}