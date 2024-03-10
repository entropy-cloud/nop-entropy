/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

public class JsonSaveOptions {
    private boolean pretty = true;
    private boolean keepComment = true;

    /**
     * 如果资源文件已存在，且内容与待保存的内容相同，则跳过保存
     */
    private boolean checkSameContent = true;

    public boolean isCheckSameContent() {
        return checkSameContent;
    }

    public void setCheckSameContent(boolean checkSameContent) {
        this.checkSameContent = checkSameContent;
    }

    public boolean isPretty() {
        return pretty;
    }

    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    public boolean isKeepComment() {
        return keepComment;
    }

    public void setKeepComment(boolean keepComment) {
        this.keepComment = keepComment;
    }
}
