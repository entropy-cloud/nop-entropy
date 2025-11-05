/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tool.log;

import io.nop.api.core.exceptions.NopException;

import java.io.BufferedReader;
import java.util.Iterator;
import java.util.NoSuchElementException;

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
        if (!hasNext())
            throw new NoSuchElementException();

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
