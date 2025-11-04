package io.nop.core.utils;

import io.nop.commons.collections.IterableIterator;
import io.nop.commons.collections.iterator.BatchingIterator;
import io.nop.commons.util.FileHelper;
import io.nop.core.model.tree.TreeVisitors;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

import static io.nop.core.model.tree.TreeVisitors.FILE_CHILDREN_ADAPTER;

public class FileIterators {
    public static IterableIterator<File> depthFirstIterator(File baseDir, boolean includeRoot,
                                                            Predicate<File> filter) {
        return TreeVisitors.depthFirstIterator(FILE_CHILDREN_ADAPTER, baseDir, includeRoot, filter);
    }

    public static IterableIterator<File> postOrderDepthFirstIterator(File baseDir, boolean includeRoot,
                                                                     Predicate<File> filter) {
        return TreeVisitors.postOrderDepthFirstIterator(FILE_CHILDREN_ADAPTER, baseDir, includeRoot, filter, true);
    }

    /**
     * 以用户给定策略，把目录下的文件聚合成若干批次
     */
    public static IterableIterator<List<File>> batchFiles(
            File baseDir,
            BatchingIterator.BatchStrategy<File> strategy,
            Predicate<File> filter) {

        IterableIterator<File> it = depthFirstIterator(baseDir, false, filter).filter(File::isFile);
        return new BatchingIterator<>(it, strategy);
    }

    /**
     * 把目录下文本文件按照“总字符数 ≤ maxChars”聚合成批次
     */
    public static IterableIterator<List<File>> batchTextFilesByCharLimitForDir(
            File baseDir, long maxChars,
            Predicate<File> filter) {

        BatchingIterator.BatchStrategy<File> strategy = batchStrategyByCharLimit(maxChars);

        return batchFiles(baseDir, strategy, filter);
    }

    public static IterableIterator<List<File>> batchTextFilesByCharLimit(Iterable<File> files, long maxChars) {
        return new BatchingIterator<>(files.iterator(), batchStrategyByCharLimit(maxChars));
    }


    public static BatchingIterator.BatchStrategy<File> batchStrategyByCharLimit(long maxChars) {
        BatchingIterator.BatchStrategy<File> strategy =
                new BatchingIterator.BatchStrategy<>() {

                    @Override
                    public Object initialState() {
                        return new long[1]; // 存放 currentChars
                    }

                    @Override
                    public Decision shouldFinishBatch(File file, List<File> batch, Object state) {
                        long len;
                        try {
                            len = FileHelper.readText(file, null).length();
                        } catch (Exception e) {
                            len = file.length();
                        }

                        long[] chars = (long[]) state;
                        if (batch.isEmpty()) { // 批次为空必须先收下
                            chars[0] = len;
                            return Decision.ACCEPT_AND_CONTINUE;
                        }
                        if (chars[0] + len <= maxChars) {
                            chars[0] += len;
                            return Decision.ACCEPT_AND_CONTINUE;
                        }
                        return Decision.REJECT_AND_FINISH;
                    }
                };
        return strategy;
    }
}
