/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 表示一个变更（diff 结果）
 */
@DataBean
public class DiffChange {
    /**
     * 变更类型
     */
    private final DiffDeltaType deltaType;

    /**
     * 原始文件起始位置（0-based，包含）
     */
    private final int startOriginal;

    /**
     * 原始文件结束位置（0-based，不包含）
     */
    private final int endOriginal;

    /**
     * 修改后文件起始位置（0-based，包含）
     */
    private final int startRevised;

    /**
     * 修改后文件结束位置（0-based，不包含）
     */
    private final int endRevised;

    public DiffChange(@JsonProperty("deltaType") DiffDeltaType deltaType,
                      @JsonProperty("startOriginal") int startOriginal,
                      @JsonProperty("endOriginal") int endOriginal,
                      @JsonProperty("startRevised") int startRevised,
                      @JsonProperty("endRevised") int endRevised) {
        this.deltaType = deltaType;
        this.startOriginal = startOriginal;
        this.endOriginal = endOriginal;
        this.startRevised = startRevised;
        this.endRevised = endRevised;
    }

    public DiffDeltaType getDeltaType() {
        return deltaType;
    }

    public int getStartOriginal() {
        return startOriginal;
    }

    public int getEndOriginal() {
        return endOriginal;
    }

    public int getStartRevised() {
        return startRevised;
    }

    public int getEndRevised() {
        return endRevised;
    }

    /**
     * 原始文件中被影响的行数
     */
    public int getOriginalSize() {
        return endOriginal - startOriginal;
    }

    /**
     * 修改后文件中被影响的行数
     */
    public int getRevisedSize() {
        return endRevised - startRevised;
    }

    /**
     * 创建新的 Change，更新原始文件结束位置
     */
    public DiffChange withEndOriginal(int endOriginal) {
        return new DiffChange(deltaType, startOriginal, endOriginal, startRevised, endRevised);
    }

    /**
     * 创建新的 Change，更新修改后文件结束位置
     */
    public DiffChange withEndRevised(int endRevised) {
        return new DiffChange(deltaType, startOriginal, endOriginal, startRevised, endRevised);
    }

    @Override
    public String toString() {
        return String.format("%s[%d,%d)->[%d,%d)",
                deltaType, startOriginal, endOriginal, startRevised, endRevised);
    }
}
