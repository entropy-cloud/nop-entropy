
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.crud.CrudBizModel;

import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaBusinessDomainBiz;
import io.nop.metadata.dao.entity.NopMetaBusinessDomain;
import io.nop.metadata.service.NopMetadataArgs;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.util.Map;

@BizModel("NopMetaBusinessDomain")
public class NopMetaBusinessDomainBizModel extends CrudBizModel<NopMetaBusinessDomain> implements INopMetaBusinessDomainBiz {
    public NopMetaBusinessDomainBizModel(){
        setEntityName(NopMetaBusinessDomain.class.getName());
    }

    // P2-28（plan 2026-08-16-0920-1）：UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME (parentDomainId,name)
    // 对根域（parentDomainId NULL）不生效（复合 UK 任一列 NULL 即豁免唯一性，NULL-distinct），
    // 根域重名失守。应用层守卫在 super.save/super.update 之后对最终实体状态查重，命中 fail-loud
    // 抛出，mutation 事务整体回滚。UK 保持不变（sentinel 改造被裁定否决，见 plan 裁决表）。
    @Override
    public NopMetaBusinessDomain save(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaBusinessDomain saved = super.save(data, context);
        rejectDuplicateRootDomainName(saved);
        return saved;
    }

    @Override
    @BizMutation
    public NopMetaBusinessDomain update(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaBusinessDomain updated = super.update(data, context);
        // P2-28：update 写入面对称防护（根域改名撞名 / 子域上提为根域撞名），与 save 共用守卫。
        rejectDuplicateRootDomainName(updated);
        return updated;
    }

    /**
     * P2-28 根域重名守卫：仅覆盖 DB UK 的 NULL-distinct 豁免面（parentDomainId IS NULL 的根域）。
     * 查重键与 UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME 对齐（name，限定根域集合），save/update
     * 带自身 id 时排除自身（幂等自更新/自改名不误伤）。非根域继续由 DB UK 数据库级拒绝，
     * 守卫不接管。沿 ERR_SQL_VIEW_TABLE_EXISTS（find-or-fail）先例。
     */
    private void rejectDuplicateRootDomainName(NopMetaBusinessDomain domain) {
        if (domain == null || domain.getParentDomainId() != null) {
            return;
        }
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.isNull(NopMetaBusinessDomain.PROP_NAME_parentDomainId));
        q.addFilter(FilterBeans.eq(NopMetaBusinessDomain.PROP_NAME_name, domain.getName()));
        boolean hasOther = dao().findAllByQuery(q).stream()
                .anyMatch(row -> !row.getBusinessDomainId().equals(domain.getBusinessDomainId()));
        if (hasOther) {
            throw new NopMetadataException(NopMetadataErrors.ERR_BUSINESS_DOMAIN_DUPLICATE_ROOT_NAME)
                    .param(NopMetadataArgs.ARG_NAME, domain.getName());
        }
    }
}
