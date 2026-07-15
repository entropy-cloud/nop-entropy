package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaDataSourceBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionService;
import jakarta.inject.Inject;

import java.util.Map;

@BizModel("NopMetaDataSource")
public class NopMetaDataSourceBizModel extends CrudBizModel<NopMetaDataSource> implements INopMetaDataSourceBiz {

    static final ErrorCode ERR_DATASOURCE_NOT_FOUND =
            ErrorCode.define("metadata.datasource-not-found",
                    "DataSource not found: {dataSourceId}", "dataSourceId");
    static final ErrorCode ERR_DATASOURCE_DISABLED =
            ErrorCode.define("metadata.datasource-disabled",
                    "DataSource is disabled, cannot test connection: {dataSourceId}", "dataSourceId");

    @Inject
    protected IMetaDataSourceConnectionService connectionService;

    public NopMetaDataSourceBizModel() {
        setEntityName(NopMetaDataSource.class.getName());
    }

    /**
     * 连通性验证：按 dataSourceId 加载 → 校验非 DISABLED → 调连接服务建连并读取 DatabaseMetaData。
     *
     * <p>设计决策 D1：
     * <ul>
     *   <li>实体不存在抛 {@code metadata.datasource-not-found}（不 NPE）</li>
     *   <li>DISABLED 抛 {@code metadata.datasource-disabled}（不静默通过）</li>
     *   <li>非 jdbc 类型抛 {@link UnsupportedOperationException}（不静默返回成功）</li>
     *   <li>connectionConfig 缺必填字段抛 {@code metadata.datasource-config-invalid}（快速失败）</li>
     *   <li>建连失败（SQLException）catch 后返回 {@code {connected:false, error}}，不向上抛，
     *       使 GraphQL 调用方拿到结构化失败结果</li>
     * </ul>
     *
     * <p>设计决策 D3：成功时从 DatabaseMetaData 识别的产品名放入返回 Map，不写回任何 ORM 列。
     */
    @BizMutation
    public Map<String, Object> testConnection(@Name("dataSourceId") String dataSourceId, IServiceContext context) {
        NopMetaDataSource dataSource = dao().getEntityById(dataSourceId);
        if (dataSource == null) {
            throw new NopException(ERR_DATASOURCE_NOT_FOUND).param("dataSourceId", dataSourceId);
        }

        String status = dataSource.getStatus();
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(status)) {
            throw new NopException(ERR_DATASOURCE_DISABLED).param("dataSourceId", dataSourceId);
        }

        return connectionService.testConnect(dataSource.getDatasourceType(), dataSource.getConnectionConfig());
    }
}
