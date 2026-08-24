package io.nop.batch.biz.importexport;

import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResource;

import java.util.concurrent.CompletionStage;

/**
 * 平台缺省的占位实现。importFile尚未实现，调用即抛UnsupportedOperationException，
 * 避免下游按接口解析调用时拿到null直接NPE且无法定位。
 * 应用需要导入功能时应提供自己的IBizEntityImporter实现并覆盖此bean。
 */
public class DefaultBizEntityImporter implements IBizEntityImporter {

    @Override
    public CompletionStage<BizEntityImportResponseBean> importFile(IResource resource,
                                                                   String bizObjName,
                                                                   BizEntityImportConfig config,
                                                                   IServiceContext context) {
        throw new UnsupportedOperationException(
                "nopDefaultBizEntityImporter is a placeholder: IBizEntityImporter.importFile is not implemented. "
                        + "Provide a custom IBizEntityImporter bean to enable entity import");
    }
}
