package io.nop.metadata.service.query;

import io.nop.dao.api.IDaoProvider;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionService;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.orm.IOrmTemplate;

import java.util.Objects;

/**
 * JOIN/聚合执行器共享的依赖上下文（架构基线 §4.4.1/§4.4.2）。
 *
 * <p>这些执行器不是 BizModel，无法直接拿 {@code dao()}/{@code orm()}。由 {@code NopMetaTableBizModel}
 * 构造本上下文并传入执行器。状态全部来自外部注入，{@link MetaQueryContext} 本身不可变（线程安全）。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link IDaoProvider} — 取各 NopMeta* 实体 DAO</li>
 *   <li>{@link IOrmTemplate} — entity 路径原生 SQL 执行载体（{@code executeQuery}）</li>
 *   <li>{@link IMetaDataSourceConnectionService} — external/sql 路径 withConnection</li>
 *   <li>无状态 helper：{@link MetaDataSourceResolver}/{@link MetaTableFieldResolver}/{@link FilterToSqlTranslator}</li>
 * </ul>
 */
public final class MetaQueryContext {
    private final IDaoProvider daoProvider;
    private final IOrmTemplate orm;
    private final IMetaDataSourceConnectionService connectionService;
    private final MetaDataSourceResolver dataSourceResolver;
    private final MetaTableFieldResolver fieldResolver;
    private final FilterToSqlTranslator filterTranslator;

    public MetaQueryContext(IDaoProvider daoProvider, IOrmTemplate orm,
                            IMetaDataSourceConnectionService connectionService,
                            MetaDataSourceResolver dataSourceResolver,
                            MetaTableFieldResolver fieldResolver,
                            FilterToSqlTranslator filterTranslator) {
        this.daoProvider = Objects.requireNonNull(daoProvider, "daoProvider");
        this.orm = Objects.requireNonNull(orm, "orm");
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService");
        this.dataSourceResolver = Objects.requireNonNull(dataSourceResolver, "dataSourceResolver");
        this.fieldResolver = Objects.requireNonNull(fieldResolver, "fieldResolver");
        this.filterTranslator = Objects.requireNonNull(filterTranslator, "filterTranslator");
    }

    public IDaoProvider daoProvider() {
        return daoProvider;
    }

    public IOrmTemplate orm() {
        return orm;
    }

    public IMetaDataSourceConnectionService connectionService() {
        return connectionService;
    }

    public MetaDataSourceResolver dataSourceResolver() {
        return dataSourceResolver;
    }

    public MetaTableFieldResolver fieldResolver() {
        return fieldResolver;
    }

    public FilterToSqlTranslator filterTranslator() {
        return filterTranslator;
    }
}
