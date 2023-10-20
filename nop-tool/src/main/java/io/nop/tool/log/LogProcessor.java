/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tool.log;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class LogProcessor {
    public void filterLog(IResource src, Predicate<String> startTest, Function<List<String>, List<String>> filter, IResource target) {
        Reader in = src.getReader(null);
        try {
            Iterator<List<String>> chunkIt = splitLines(new BufferedReader(in), startTest);
            saveToResource(target, chunkIt, filter);
        } finally {
            IoHelper.safeCloseObject(in);
        }
    }

    private Iterator<List<String>> splitLines(BufferedReader in, Predicate<String> startTest) {
        return new ChunkIterator<>(new LineIterator(in), startTest);
    }

    private void saveToResource(IResource target, Iterator<List<String>> chunkIt, Function<List<String>, List<String>> filter) {
        Writer out = target.getWriter(null);
        try {
            BufferedWriter bout = new BufferedWriter(out);
            while (chunkIt.hasNext()) {
                List<String> chunk = chunkIt.next();
                chunk = filter.apply(chunk);
                if (chunk == null || chunk.isEmpty())
                    continue;
                writeChunk(bout, chunk);
            }
            bout.flush();
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(out);
        }
    }

    private void writeChunk(BufferedWriter out, List<String> chunk) throws IOException {
        for (String line : chunk) {
            out.write(line);
            out.write('\n');
        }
    }
}
