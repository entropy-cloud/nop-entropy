
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.exceptions.ErrorCode;

import io.nop.datav.biz.INopDatavAlertStateBiz;
import io.nop.datav.dao.entity.NopDatavAlertState;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_STATE_STD_MUTATION_NOT_ALLOWED;

/**
 * 告警状态 BizModel（P1-10 修复，plan 2026-08-15-2146-1，裁定 D5 方案 c）。
 *
 * <p>AlertState 状态机的唯一写入点是 AlertEvaluator（DAO 直写）：标准 CRUD mutation 面
 * （save/update/delete/batchDelete 等 13 个入口）经 {@link NopDatavSingleWriterCrudBizModel}
 * 显式拒绝，防止经继承 mutation 越过状态机改写状态。</p>
 */
@BizModel("NopDatavAlertState")
public class NopDatavAlertStateBizModel extends NopDatavSingleWriterCrudBizModel<NopDatavAlertState>
        implements INopDatavAlertStateBiz {
    public NopDatavAlertStateBizModel() {
        setEntityName(NopDatavAlertState.class.getName());
    }

    @Override
    protected ErrorCode stdMutationNotAllowedError() {
        return ERR_DATAV_ALERT_STATE_STD_MUTATION_NOT_ALLOWED;
    }
}
