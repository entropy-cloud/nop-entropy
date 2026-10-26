package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaMethodArgBiz;
import io.nop.metadata.dao.entity.NopMetaMethodArg;

/**
 * 方法参数子表 BizModel（设计 14 §3.1，plan 2026-10-26-2047-1 Phase 1）。
 *
 * <p>argName 方法内唯一由 DB UK {@code UK_NOP_META_METHOD_ARG_NAME} 承载（平台转译为
 * {@code nop.err.biz.entity-already-exists}）。无 NULL-distinct 豁免面（methodId/argName
 * 均 mandatory），按 P2-28 先例应用层守卫不接管 UK 已覆盖面，本类保持标准 CRUD 薄壳。
 */
@BizModel("NopMetaMethodArg")
public class NopMetaMethodArgBizModel extends CrudBizModel<NopMetaMethodArg>
        implements INopMetaMethodArgBiz {

    public NopMetaMethodArgBizModel() {
        setEntityName(NopMetaMethodArg.class.getName());
    }
}
