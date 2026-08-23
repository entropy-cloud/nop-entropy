/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.sys.biz.INopSysSequenceBiz;
import io.nop.sys.dao.entity.NopSysSequence;
import io.nop.sys.dao.seq.SysSequenceGenerator;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

@BizModel("NopSysSequence")
public class NopSysSequenceBizModel extends CrudBizModel<NopSysSequence> implements INopSysSequenceBiz {
    /**
     * 序列配置变更后必须失效生成器缓存：SeqItem按JVM永久缓存且syncFromDb会用内存轨迹写回
     * nextValue，不清理会导致管理端回拨nextValue修重号静默失效、cacheSize/stepSize调整不生效。
     */
    @Inject
    @Nullable
    protected SysSequenceGenerator sequenceGenerator;

    public NopSysSequenceBizModel(){
        setEntityName(NopSysSequence.class.getName());
    }

    @BizAction
    @Override
    protected void afterEntityChange(@Name("entity") NopSysSequence entity, @Name("action") String action,
                                     IServiceContext context) {
        super.afterEntityChange(entity, action, context);
        if (sequenceGenerator != null)
            sequenceGenerator.removeCache(entity.getSeqName());
    }
}
