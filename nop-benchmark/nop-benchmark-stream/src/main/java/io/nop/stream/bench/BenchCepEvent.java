/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

/**
 * CEP 基准事件：仅携带一个序号与一个匹配标志位，条件判断零分配。
 */
public class BenchCepEvent {

    private long id;
    private boolean matching;

    public BenchCepEvent() {
    }

    public BenchCepEvent(long id, boolean matching) {
        this.id = id;
        this.matching = matching;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isMatching() {
        return matching;
    }

    public void setMatching(boolean matching) {
        this.matching = matching;
    }
}
