package io.nop.ai.coder.file;

import io.nop.api.core.beans.IntRangeBean;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FileDiffApplier {
    private final IFileOperator fileOperator;

    public FileDiffApplier(IFileOperator fileOperator) {
        this.fileOperator = fileOperator;
    }

    /**
     * 应用文件差异
     *
     * @param fileDiff 文件差异描述
     */
    public void apply(FileDiff fileDiff) {
        if (fileDiff.isAddedFile()) {
            applyAddFile(fileDiff);
        } else if (fileDiff.isDeletedFile()) {
            applyDeleteFile(fileDiff);
        } else if (fileDiff.isRenamedFile()) {
            applyRenameFile(fileDiff);
        } else {
            applyModifyFile(fileDiff);
        }
    }

    private void applyAddFile(FileDiff fileDiff) {
        String newPath = fileDiff.getNewPath();
        String content = fileDiff.getDiffs().stream()
                .flatMap(section -> section.getChangedLines().stream())
                .collect(Collectors.joining("\n"));

        fileOperator.writeFileContent(new FileContent(newPath, null, content));
    }

    private void applyDeleteFile(FileDiff fileDiff) {
        fileOperator.delete(fileDiff.getOldPath());
    }

    private void applyRenameFile(FileDiff fileDiff) {
        fileOperator.move(fileDiff.getOldPath(), fileDiff.getNewPath());
    }

    private void applyModifyFile(FileDiff fileDiff) {
        String path = fileDiff.getNewPath();
        List<String> lines = fileOperator.readLines(path);

        List<IntRangeBean> positions = fileDiff.getDiffs().stream().map(section -> {
            return findApplicationPosition(lines, section);
        }).collect(Collectors.toList());

        applyDiffSection(lines, fileDiff.getDiffs(), positions);

        fileOperator.writeFileContent(new FileContent(path, null, String.join("\n", lines)));
    }


    /**
     * 查找差异应用的位置范围（改进版，处理变更行数不匹配的情况）
     *
     * @param lines   文件内容行列表
     * @param section 差异块
     * @return 包含起始位置(包含)和结束位置(不包含)的范围对象
     * @throws IllegalStateException 如果无法定位差异位置
     */
    private IntRangeBean findApplicationPosition(List<String> lines, FileDiff.DiffSection section) {
        List<String> leadingContext = section.getLeadingContext();
        List<String> trailingContext = section.getTrailingContext();
        List<String> changedLines = section.getChangedLines();

        // 空文件或没有前导上下文的情况
        if (lines.isEmpty()) {
            return IntRangeBean.EMPTY;
        }

        // 1. 首先尝试匹配完整上下文（前导+后随）
        if (!leadingContext.isEmpty() && !trailingContext.isEmpty()) {
            for (int i = 0; i <= lines.size() - leadingContext.size(); i++) {
                // 匹配前导上下文
                if (matchContext(lines, i, leadingContext)) {
                    // 在前导上下文之后查找后随上下文
                    int remainingLines = lines.size() - (i + leadingContext.size());
                    if (remainingLines >= trailingContext.size()) {
                        int trailingStart = findTrailingContextStart(
                                lines,
                                i + leadingContext.size(),
                                trailingContext,
                                changedLines
                        );

                        if (trailingStart != -1) {
                            // 返回前导上下文之后到后随上下文之前的位置
                            return IntRangeBean.build(
                                    i + leadingContext.size(),
                                    trailingStart
                            );
                        }
                    }
                }
            }
        }

        // 2. 尝试只匹配前导上下文
        if (!leadingContext.isEmpty()) {
            for (int i = 0; i <= lines.size() - leadingContext.size(); i++) {
                if (matchContext(lines, i, leadingContext)) {
                    // 返回前导上下文之后的位置
                    return IntRangeBean.build(
                            i + leadingContext.size(),
                            i + leadingContext.size() + (changedLines != null ? changedLines.size() : 0)
                    );
                }
            }
        }

        // 3. 尝试只匹配后随上下文
        if (!trailingContext.isEmpty()) {
            for (int i = 0; i <= lines.size() - trailingContext.size(); i++) {
                if (matchContext(lines, i, trailingContext)) {
                    // 返回后随上下文之前的位置
                    return IntRangeBean.build(
                            Math.max(0, i - (changedLines != null ? changedLines.size() : 0)),
                            i
                    );
                }
            }
        }

        throw new IllegalStateException("Cannot find application position for diff section: \n" +
                "Leading context: " + leadingContext + "\n" +
                "Changed lines: " + changedLines + "\n" +
                "Trailing context: " + trailingContext);
    }

    /**
     * 在前导上下文之后查找后随上下文的起始位置
     */
    private int findTrailingContextStart(
            List<String> lines,
            int searchStart,
            List<String> trailingContext,
            List<String> changedLines
    ) {
        int maxSearchEnd = lines.size() - trailingContext.size();

        // 如果有变更行，尝试在合理范围内查找
        if (changedLines != null && !changedLines.isEmpty()) {
            int expectedPosition = searchStart + changedLines.size();
            if (expectedPosition <= maxSearchEnd) {
                if (matchContext(lines, expectedPosition, trailingContext)) {
                    return expectedPosition;
                }
            }
        }

        // 如果没有变更行或未找到，放宽搜索范围
        for (int i = searchStart; i <= maxSearchEnd; i++) {
            if (matchContext(lines, i, trailingContext)) {
                return i;
            }
        }

        return -1;
    }

    private boolean matchContext(List<String> lines, int startPos, List<String> context) {
        if (startPos < 0 || startPos + context.size() > lines.size()) {
            return false;
        }

        for (int i = 0; i < context.size(); i++) {
            if (!lines.get(startPos + i).equals(context.get(i))) {
                return false;
            }
        }
        return true;
    }

    private void applyDiffSection(List<String> lines,
                                  List<FileDiff.DiffSection> sections,
                                  List<IntRangeBean> originalPositions) {
        // 创建一个位置调整器来跟踪行号变化
        PositionAdjuster adjuster = new PositionAdjuster();

        // 按照原始位置顺序处理（不需要反向）
        for (int i = 0; i < sections.size(); i++) {
            FileDiff.DiffSection section = sections.get(i);
            IntRangeBean originalRange = originalPositions.get(i);

            // 获取调整后的位置
            IntRangeBean adjustedRange = adjuster.adjust(originalRange);

            // 应用差异块
            applySingleDiffSection(lines, section, adjustedRange);

            // 记录这次修改对后续位置的影响
            adjuster.recordChange(originalRange, section);
        }
    }

    // 应用单个差异块
    private void applySingleDiffSection(List<String> lines,
                                        FileDiff.DiffSection section,
                                        IntRangeBean range) {
        switch (section.getType()) {
            case ADD:
                if (range.getStart() <= lines.size()) {
                    lines.addAll(range.getStart(), section.getChangedLines());
                }
                break;

            case DELETE:
                int deleteEnd = Math.min(range.getEnd(), lines.size());
                if (range.getStart() < deleteEnd) {
                    lines.subList(range.getStart(), deleteEnd).clear();
                }
                break;

            case MODIFY:
                int modifyEnd = Math.min(range.getEnd(), lines.size());
                if (range.getStart() < modifyEnd) {
                    lines.subList(range.getStart(), modifyEnd).clear();
                }
                if (range.getStart() <= lines.size()) {
                    lines.addAll(range.getStart(), section.getChangedLines());
                }
                break;
        }
    }

    /**
     * 位置调整器，跟踪每次修改对后续位置的影响
     */
    private static class PositionAdjuster {
        private final List<PositionChange> changes = new ArrayList<>();

        public IntRangeBean adjust(IntRangeBean originalRange) {
            int adjustedStart = originalRange.getStart();
            int adjustedEnd = originalRange.getEnd();

            for (PositionChange change : changes) {
                if (change.affects(originalRange)) {
                    adjustedStart = change.adjustPosition(adjustedStart);
                    adjustedEnd = change.adjustPosition(adjustedEnd);
                }
            }

            return new IntRangeBean(adjustedStart, adjustedEnd);
        }

        public void recordChange(IntRangeBean originalRange, FileDiff.DiffSection section) {
            int position = originalRange.getStart();
            int delta = calculateDelta(section, originalRange);
            changes.add(new PositionChange(position, delta));
        }

        private int calculateDelta(FileDiff.DiffSection section, IntRangeBean range) {
            switch (section.getType()) {
                case ADD:
                    return section.getChangedLines().size();
                case DELETE:
                    return -(range.getEnd() - range.getStart());
                case MODIFY:
                    return section.getChangedLines().size() - (range.getEnd() - range.getStart());
                default:
                    return 0;
            }
        }

        private static class PositionChange {
            private final int anchorPosition;
            private final int delta;

            PositionChange(int anchorPosition, int delta) {
                this.anchorPosition = anchorPosition;
                this.delta = delta;
            }

            boolean affects(IntRangeBean range) {
                return anchorPosition <= range.getStart();
            }

            int adjustPosition(int originalPos) {
                if (originalPos >= anchorPosition) {
                    return originalPos + delta;
                }
                return originalPos;
            }
        }
    }
}