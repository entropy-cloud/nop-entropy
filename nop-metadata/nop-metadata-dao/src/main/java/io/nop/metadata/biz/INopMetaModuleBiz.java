
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.core.dto.ImportOrmModelResultDTO;
import io.nop.metadata.dao.entity.NopMetaManifest;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.orm.biz.ICrudBiz;

import java.util.List;


/**
 * NopMetaModule BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>跨模块 {@code @Inject INopMetaModuleBiz} 调用入口：
 * importOrmModel / importOrmModels / releaseModule / generateManifest。
 */
public interface INopMetaModuleBiz extends ICrudBiz<NopMetaModule> {

    @BizMutation
    NopMetaModule importOrmModel(@Name("path") String path, IServiceContext context);

    @BizMutation
    List<ImportOrmModelResultDTO> importOrmModels(@Name("paths") List<String> paths, IServiceContext context);

    @BizMutation
    NopMetaModule releaseModule(@Name("metaModuleId") String metaModuleId, IServiceContext context);

    @BizMutation
    NopMetaManifest generateManifest(@Name("metaModuleId") String metaModuleId, IServiceContext context);
}
