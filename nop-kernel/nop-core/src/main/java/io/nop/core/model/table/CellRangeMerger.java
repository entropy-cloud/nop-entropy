/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table;

import io.nop.commons.collections.IntHashMap;
import io.nop.commons.collections.MapOfInt;

import java.util.ArrayList;
import java.util.List;

public class CellRangeMerger {
    static class Range {
        int firstIndex;
        int lastIndex;

        Range(int index) {
            this.firstIndex = this.lastIndex = index;
        }

        Range(int firstIndex, int lastIndex) {
            this.firstIndex = firstIndex;
            this.lastIndex = lastIndex;
        }

        Range copy() {
            return new Range(firstIndex, lastIndex);
        }
    }

    static class Block {
        int firstRowIndex;
        int lastRowIndex;
        int firstColIndex;
        int lastColIndex;

        Block(Range range, int colIndex) {
            this.firstRowIndex = range.firstIndex;
            this.lastRowIndex = range.lastIndex;
            this.firstColIndex = this.lastColIndex = colIndex;
        }

        Block(int firstRowIndex, int lastRowIndex, int colIndex) {
            this.firstRowIndex = firstRowIndex;
            this.lastRowIndex = lastRowIndex;
            this.firstColIndex = this.lastColIndex = colIndex;
        }

        Block(int firstRowIndex, int lastRowIndex, int firstColIndex, int lastColIndex) {
            this.firstRowIndex = firstRowIndex;
            this.lastRowIndex = lastRowIndex;
            this.firstColIndex = firstColIndex;
            this.lastColIndex = lastColIndex;
        }


        public String toString() {
            return toCellRange().toString();
        }

        CellRange toCellRange() {
            return new CellRange(firstRowIndex, firstColIndex, lastRowIndex, lastColIndex);
        }
    }

    /**
     * 按照列映射到排好序的区间列表中
     */
    private final MapOfInt<List<Range>> colRanges = new IntHashMap<>();
    private int minRowIndex = Integer.MAX_VALUE;
    private int maxRowIndex = -1;
    private int minColIndex = Integer.MAX_VALUE;
    private int maxColIndex = -1;

    public String display() {
        StringBuilder sb = new StringBuilder();
        int n = maxRowIndex + 1;
        int m = maxColIndex + 1;

        for (int i = 0; i < n; i++) {
            if (i != 0)
                sb.append('\n');
            for (int j = 0; j < m; j++) {
                sb.append('0');
            }
        }

        colRanges.forEachEntry((list, colIndex) -> {
            for (Range range : list) {
                for (int i = range.firstIndex; i <= range.lastIndex; i++) {
                    int pos = i * (m + 1) + colIndex;
                    sb.setCharAt(pos, '1');
                }
            }
        });
        return sb.toString();
    }

    public List<CellRange> getMergedRanges() {
        List<CellRange> ret = new ArrayList<>();

        List<Block> blocks = new ArrayList<>();

        for (int colIndex = minColIndex; colIndex <= maxColIndex; colIndex++) {
            List<Range> list = colRanges.get(colIndex);
            if (list == null) {
                // 没有可合并的列
                toRanges(blocks, ret);
            } else {
                int index = 0;
                for (Range range : list) {
                    Range r = range.copy();
                    do {
                        index = mergeRange(index, colIndex, r, blocks, ret);
                        if (index < 0) {
                            index = -index;
                            break;
                        }
                    } while (true);
                }
            }
        }
        toRanges(blocks, ret);
        return ret;
    }

    private int mergeRange(int index, int colIndex, Range range, List<Block> blocks, List<CellRange> ret) {
        for (int i = index; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (range.firstIndex > block.lastRowIndex) {
                Block completed = blocks.remove(i);
                ret.add(completed.toCellRange());
                i--;
                continue;
            }

            if (range.lastIndex < block.firstRowIndex) {
                // range与block不重叠
                Block prev = new Block(range, colIndex);
                blocks.add(i, prev);
                return -(i + 1);
            } else if (range.firstIndex <= block.firstRowIndex) {
                if (range.firstIndex < block.firstRowIndex) {
                    // range与block重叠，需要拆分区间
                    Block block1 = new Block(range.firstIndex, block.firstRowIndex - 1, colIndex);
                    blocks.add(i, block1);
                    i++;
                }
                if (range.lastIndex < block.lastRowIndex) {
                    // block的前半部分与range合并
                    Block block2 = new Block(block.firstRowIndex, range.lastIndex, block.firstColIndex,colIndex);
                    blocks.add(i, block2);
                    i++;
                    // 缩减block的部分
                    block.firstRowIndex = range.lastIndex + 1;
                } else if (range.lastIndex == block.lastRowIndex) {
                    block.lastColIndex++;
                } else {
                    block.lastColIndex++;
                    // 缩减range，尝试下一个block
                    range.firstIndex = block.lastRowIndex + 1;
                    return i + 1;
                }
                return -(i + 1);
            } else {
                // block.firstRowIndex < range.firstIndex <= block.lastRowIndex
                CellRange block1 = new CellRange(block.firstRowIndex, block.firstColIndex, range.firstIndex - 1, block.lastColIndex);
                ret.add(block1);
                if (range.lastIndex < block.lastRowIndex) {
                    Block block2 = new Block(range.firstIndex, range.lastIndex, block.firstColIndex, colIndex);
                    blocks.add(i, block2);
                    block.firstRowIndex = range.lastIndex + 1;
                    return -(i + 1);
                } else if (range.lastIndex == block.lastRowIndex) {
                    block.firstRowIndex = range.firstIndex;
                    block.lastColIndex++;
                    return -(i + 1);
                } else {
                    // range.lastIndex > block.lastRowIndex
                    block.firstRowIndex = range.firstIndex;
                    block.lastColIndex++;
                    range.firstIndex = block.lastRowIndex + 1;
                    return i + 1;
                }
            }
        }

        Block next = new Block(range, colIndex);
        blocks.add(next);
        return -blocks.size();
    }

    private void toRanges(List<Block> blocks, List<CellRange> ret) {
        for (Block block : blocks) {
            ret.add(block.toCellRange());
        }
        blocks.clear();
    }

    public void addCell(int rowIndex, int colIndex) {
        this.minRowIndex = Math.min(this.minRowIndex, rowIndex);
        this.minColIndex = Math.min(this.minColIndex, colIndex);
        this.maxRowIndex = Math.max(this.maxRowIndex, rowIndex);
        this.maxColIndex = Math.max(this.maxColIndex, colIndex);

        List<Range> list = colRanges.get(colIndex);
        if (list == null) {
            list = new ArrayList<>();
            colRanges.put(colIndex, list);

            Range range = new Range(rowIndex);
            list.add(range);
        } else {
            int idx = binarySearch(list, rowIndex);
            // 如果找到匹配单元，则已经存在，可以直接忽略。
            // 如果不存在，则考虑是延展已有的区间，还是插入新的区间
            if (idx < 0) {
                int low = -idx - 1;
                if (low > 0) {
                    Range prev = list.get(low - 1);
                    if (prev != null) {
                        if (prev.lastIndex >= rowIndex) return;
                        if (prev.lastIndex + 1 == rowIndex) {
                            // 延展前方的区间
                            prev.lastIndex++;

                            if (low < list.size()) {
                                Range next = list.get(low);
                                // 与后一个区间合并
                                if (next != null && next.firstIndex == prev.lastIndex) {
                                    prev.lastIndex = next.lastIndex;
                                    list.remove(low);
                                }
                            }
                            return;
                        }
                    }
                }

                if (low + 1 < list.size()) {
                    Range next = list.get(low + 1);
                    if (next != null) {
                        // 此时前方区间肯定不会与next进行合并
                        if (next.firstIndex == rowIndex + 1) {
                            next.firstIndex = rowIndex;
                            return;
                        }
                    }
                }
                Range range = new Range(rowIndex);
                list.add(low, range);
            }
        }
    }

    private int binarySearch(List<Range> list, int rowIndex) {
        int low = 0;
        int high = list.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            Range midVal = list.get(mid);
            int cmp = Integer.compare(midVal.firstIndex, rowIndex);

            if (cmp < 0) low = mid + 1;
            else if (cmp > 0) high = mid - 1;
            else return mid; // key found
        }
        return -(low + 1);  // key not found
    }
}
