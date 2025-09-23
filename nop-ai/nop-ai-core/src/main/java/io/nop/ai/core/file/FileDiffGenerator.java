package io.nop.ai.core.file;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class FileDiffGenerator {
    private static final int CONTEXT_SIZE = 3;
    private static final int CONTEXT_MERGE_THRESHOLD = 2;

    private enum EditType {
        KEEP, INSERT, DELETE
    }

    private static class Edit {
        final EditType type;
        final int oldPos;
        final int newPos;

        Edit(EditType type, int oldPos, int newPos) {
            this.type = Objects.requireNonNull(type);
            this.oldPos = oldPos;
            this.newPos = newPos;
        }
    }

    private static class Snake {
        final int x, y;
        final Snake prev;
        final EditType editType;

        Snake(int x, int y, Snake prev, EditType editType) {
            this.x = x;
            this.y = y;
            this.prev = prev;
            this.editType = editType;
        }
    }

    public FileDiff compileFileContent(List<String> oldLines, List<String> newLines) {
        Objects.requireNonNull(oldLines, "oldLines must not be null");
        Objects.requireNonNull(newLines, "newLines must not be null");

        if (oldLines.isEmpty() && !newLines.isEmpty()) {
            return createFullDiff(newLines, FileDiff.DiffType.ADD);
        }
        if (!oldLines.isEmpty() && newLines.isEmpty()) {
            return createFullDiff(oldLines, FileDiff.DiffType.DELETE);
        }

        List<Edit> edits = computeEditsWithSnakes(oldLines, newLines);
        List<FileDiff.DiffSection> sections = mergeEditsWithContext(edits, oldLines, newLines);

        FileDiff fileDiff = new FileDiff();
        fileDiff.setDiffs(sections);
        return fileDiff;
    }

    private FileDiff createFullDiff(List<String> lines, FileDiff.DiffType type) {
        FileDiff fileDiff = new FileDiff();
        FileDiff.DiffSection section = new FileDiff.DiffSection();
        section.setType(type);
        section.setChangedLines(new ArrayList<>(lines));
        fileDiff.setDiffs(Collections.singletonList(section));
        return fileDiff;
    }

    private List<Edit> computeEditsWithSnakes(List<String> oldLines, List<String> newLines) {
        int N = oldLines.size();
        int M = newLines.size();
        int maxD = N + M;
        int size = 2 * maxD + 1;

        int[] V = new int[size];
        Snake[] snakes = new Snake[size];

        V[maxD + 1] = 0;
        snakes[maxD + 1] = new Snake(0, 0, null, EditType.KEEP);

        for (int D = 0; D <= maxD; D++) {
            for (int k = -D; k <= D; k += 2) {
                int index = k + maxD;
                boolean down = (k == -D || (k != D && V[index - 1] < V[index + 1]));

                Snake prevSnake;
                int x;
                if (down) {
                    x = V[index + 1];
                    prevSnake = snakes[index + 1];
                } else {
                    x = V[index - 1] + 1;
                    prevSnake = snakes[index - 1];
                }

                int y = x - k;
                Snake currentSnake = new Snake(x, y, prevSnake, down ? EditType.INSERT : EditType.DELETE);

                // 沿对角线移动直到差异点
                while (x < N && y < M && oldLines.get(x).equals(newLines.get(y))) {
                    x++;
                    y++;
                    currentSnake = new Snake(x, y, currentSnake, EditType.KEEP);
                }

                V[index] = x;
                snakes[index] = currentSnake;

                if (x >= N && y >= M) {
                    return buildEditPath(currentSnake);
                }
            }
        }
        return Collections.emptyList();
    }

    private List<Edit> buildEditPath(Snake snake) {
        List<Edit> edits = new ArrayList<>();
        while (snake != null && snake.prev != null) {
            if (snake.editType != EditType.KEEP) {
                edits.add(new Edit(snake.editType, snake.prev.x, snake.prev.y));
            }
            snake = snake.prev;
        }
        Collections.reverse(edits);
        return edits;
    }

    private List<FileDiff.DiffSection> mergeEditsWithContext(
            List<Edit> edits, List<String> oldLines, List<String> newLines) {
        List<FileDiff.DiffSection> sections = new ArrayList<>();
        int i = 0;
        while (i < edits.size()) {
            Edit edit = edits.get(i);
            if (edit.type == EditType.KEEP) {
                i++;
                continue;
            }

            FileDiff.DiffSection section = new FileDiff.DiffSection();
            section.setType(determineDiffType(edit.type));

            int oldStart = edit.oldPos;
            int oldEnd = oldStart; // 在旧文件中的结束位置
            List<String> changedLines = new ArrayList<>();

            // 处理当前编辑
            if (edit.type == EditType.INSERT) {
                changedLines.add(newLines.get(edit.newPos));
            } else if (edit.type == EditType.DELETE) {
                // 删除操作不添加内容，但标记位置
                oldEnd = oldStart + 1;
            }
            i++;

            // 合并后续编辑
            while (i < edits.size()) {
                Edit current = edits.get(i);
                int gap = current.oldPos - oldEnd;
                if (gap < 0) {
                    // 顺序错误，结束合并
                    break;
                }

                if (current.type != edit.type) {
                    if (gap > CONTEXT_MERGE_THRESHOLD) {
                        break;
                    }
                    // 混合编辑类型转为MODIFY
                    section.setType(FileDiff.DiffType.MODIFY);
                } else if (gap > CONTEXT_MERGE_THRESHOLD) {
                    // 相同类型但间隔过大
                    break;
                }

                // 处理后续编辑
                if (current.type == EditType.INSERT) {
                    changedLines.add(newLines.get(current.newPos));
                } else if (current.type == EditType.DELETE) {
                    oldEnd = current.oldPos + 1;
                }
                i++;
            }

            section.setChangedLines(changedLines);
            addContextWithBounds(section, oldLines, oldStart, oldEnd);
            sections.add(section);
        }
        return sections;
    }

    private void addContextWithBounds(FileDiff.DiffSection section,
                                      List<String> oldLines,
                                      int oldStart, int oldEnd) {
        // 前导上下文（确保不越界）
        int contextStart = Math.max(0, oldStart - CONTEXT_SIZE);
        section.setLeadingContext(new ArrayList<>(
                oldLines.subList(contextStart, oldStart)));

        // 后随上下文（确保不越界）
        int contextEnd = Math.min(oldLines.size(), oldEnd + CONTEXT_SIZE);
        section.setTrailingContext(new ArrayList<>(
                oldLines.subList(oldEnd, contextEnd)));
    }

    private FileDiff.DiffType determineDiffType(EditType editType) {
        switch (editType) {
            case INSERT:
                return FileDiff.DiffType.ADD;
            case DELETE:
                return FileDiff.DiffType.DELETE;
            default:
                throw new IllegalArgumentException("Invalid edit type");
        }
    }
}