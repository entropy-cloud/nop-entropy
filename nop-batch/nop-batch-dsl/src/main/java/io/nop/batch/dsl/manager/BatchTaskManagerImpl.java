package io.nop.batch.dsl.manager;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.manager.IBatchTaskManager;
import io.nop.batch.dsl.BatchDslConstants;
import io.nop.batch.dsl.model.BatchTaskModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import static io.nop.batch.dsl.BatchDslErrors.ERR_BATCH_TASK_NAME_EMPTY;

public class BatchTaskManagerImpl implements IBatchTaskManager {

    private IOrmTemplate ormTemplate;

    private IJdbcTemplate jdbcTemplate;

    private IDaoProvider daoProvider;

    private IBatchStateStore stateStore;

    private ITransactionTemplate transactionTemplate;

    private ISqlLibManager sqlLibManager;

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setStateStore(@Nullable IBatchStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Inject
    public void setSqlLibManager(ISqlLibManager sqlLibManager) {
        this.sqlLibManager = sqlLibManager;
    }

    @Override
    public IBatchTaskContext newBatchTaskContext(IServiceContext svcCtx, IEvalScope scope) {
        return new BatchTaskContextImpl(svcCtx, scope);
    }

    @Override
    public IBatchTask newBatchTask(String batchTaskName, Long batchTaskVersion, IBeanProvider beanProvider) {
        BatchTaskModel taskModel = loadBatchTaskModel(batchTaskName, batchTaskVersion);
        return new ModelBasedBatchTaskBuilderFactory(taskModel, stateStore, transactionTemplate,
                ormTemplate, jdbcTemplate, daoProvider, sqlLibManager).newTaskBuilder(beanProvider).buildTask();
    }

    @Override
    public IBatchTask newBatchTaskFromModel(XNode node, IBeanProvider beanProvider, IXLangCompileScope scope) {
        XLangCompileTool compileTool = scope == null ? XLang.newCompileTool() : new XLangCompileTool(scope.newChildScope(true));
        boolean allowUnregisteredVar = compileTool.isAllowUnregisteredScopeVar();
        try {
            compileTool.allowUnregisteredScopeVar(true);
            BatchTaskModel taskModel = (BatchTaskModel) new DslModelParser(BatchDslConstants.XDEF_BATCH).withCompileTool(compileTool).parseFromNode(node);
            if (StringHelper.isEmpty(taskModel.getTaskName()) && Boolean.TRUE.equals(taskModel.getSaveState()))
                throw new NopException(ERR_BATCH_TASK_NAME_EMPTY).source(node);

            return new ModelBasedBatchTaskBuilderFactory(taskModel, stateStore, transactionTemplate,
                    ormTemplate, jdbcTemplate, daoProvider, sqlLibManager).newTaskBuilder(beanProvider).buildTask();
        } finally {
            compileTool.allowUnregisteredScopeVar(allowUnregisteredVar);
        }
    }

    public BatchTaskModel loadBatchTaskModel(String batchTaskName, Long taskVersion) {
        String taskPath = ResourceVersionHelper.buildPath(BatchDslConstants.FILE_DIR_BATCH_TASK,
                batchTaskName, taskVersion, BatchDslConstants.FILE_TYPE_BATCH_TASK);
        return (BatchTaskModel) ResourceComponentManager.instance().loadComponentModel(taskPath);
    }
}
