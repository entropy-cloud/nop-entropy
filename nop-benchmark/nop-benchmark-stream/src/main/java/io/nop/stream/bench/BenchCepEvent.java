/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.bench;

import io.nop.api.core.annotations.data.DataBean;

/**
 * CEP 基准事件：仅携带一个序号与一个匹配标志位，条件判断零分配。
 * 标注 @DataBean：RocksDB 状态后端经 JSON 序列化事件桶，非 DataBean 类型会被拒绝。
 */
@DataBean
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
