/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tool.log;

import io.nop.api.core.exceptions.NopException;

import java.io.BufferedReader;
import java.util.Iterator;

public class LineIterator implements Iterator<String> {
    private final BufferedReader in;

    private String line;

    public LineIterator(BufferedReader in) {
        this.in = in;
        tryReadNext();
    }

    @Override
    public boolean hasNext() {
        return line != null;
    }

    @Override
    public String next() {
        String ret = line;
        tryReadNext();
        return ret;
    }

    private void tryReadNext() {
        try {
            line = in.readLine();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
