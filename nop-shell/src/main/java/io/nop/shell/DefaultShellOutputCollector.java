/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.shell;

public class DefaultShellOutputCollector implements IShellOutputCollector {
    private final StringBuilder output = new StringBuilder();
    private final StringBuilder error = new StringBuilder();

    private final String lineSeparator;

    public DefaultShellOutputCollector(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    public DefaultShellOutputCollector() {
        this(System.getProperty("line.separator"));
    }

    @Override
    public void onOutput(String line) {
        output.append(line);
        output.append(lineSeparator);
    }

    @Override
    public void onError(String line) {
        error.append(line);
        error.append(lineSeparator);
    }

    public String getOutput() {
        return output.toString();
    }

    public String getError() {
        return error.toString();
    }
}
