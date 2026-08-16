
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.exceptions.ErrorCode;

import io.nop.datav.biz.INopDatavDashboardSnapshotBiz;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_STD_MUTATION_NOT_ALLOWED;

/**
 * 看板发布快照 BizModel（P1-10 修复，plan 2026-08-15-2146-1，裁定 D5 方案 c）。
 *
 * <p>快照 append-only：标准 CRUD mutation 面（save/update/delete/batchDelete 等 13 个入口）经
 * {@link NopDatavSingleWriterCrudBizModel} 显式拒绝；唯一写入点为 {@code publishDashboard}/
 * {@code rollbackDashboard}（DAO 直写，不经本 BizModel mutation action）。</p>
 */
@BizModel("NopDatavDashboardSnapshot")
public class NopDatavDashboardSnapshotBizModel extends NopDatavSingleWriterCrudBizModel<NopDatavDashboardSnapshot>
        implements INopDatavDashboardSnapshotBiz {
    public NopDatavDashboardSnapshotBizModel() {
        setEntityName(NopDatavDashboardSnapshot.class.getName());
    }

    @Override
    protected ErrorCode stdMutationNotAllowedError() {
        return ERR_DATAV_SNAPSHOT_STD_MUTATION_NOT_ALLOWED;
    }
}
