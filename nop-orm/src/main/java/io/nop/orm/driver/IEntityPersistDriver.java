package io.nop.orm.driver;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.commons.collections.IntArray;
import io.nop.dao.shard.ShardSelection;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.session.IOrmSessionImplementor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 具体负责与外部数据源交互的接口。不处理全局缓存和session缓存, 也不处理shard选择。
 */
public interface IEntityPersistDriver extends AutoCloseable {

    void init(IEntityModel entityModel, IPersistEnv env);

    <T> T getExtension(Class<T> clazz);

    IOrmEntity findLatest(ShardSelection selection, IOrmEntity entity, IOrmSessionImplementor session);

    CompletionStage<Boolean> loadAsync(ShardSelection shard, IOrmEntity entity, IntArray propIds, IOrmSessionImplementor session);

    boolean lock(ShardSelection shard, IOrmEntity entity, IntArray propIds, Runnable unlockCallback,
                 IOrmSessionImplementor session);

    /**
     * 本函数会被执行两次，第一次按照topoAsc顺序，另一次按照topoDesc顺序。一般情况下删除应该在topoDesc阶段执行，其他按照topoAsc阶段执行
     *
     * @param topoAsc       实体按照依赖关系进行拓扑排序。topoAsc=true表示按照拓扑排序后的顺序执行
     * @param saveActions   可能为null
     * @param updateActions 可能为null
     * @param deleteActions 可能为null
     */
    CompletionStage<Void> batchExecuteAsync(boolean topoAsc, String querySpace, List<IBatchAction.EntitySaveAction> saveActions,
                                      List<IBatchAction.EntityUpdateAction> updateActions,
                                      List<IBatchAction.EntityDeleteAction> deleteActions,
                                      IOrmSessionImplementor session);

    CompletionStage<Void> batchLoadAsync(ShardSelection shard, Collection<IOrmEntity> entities, IntArray propIds,
                        FieldSelectionBean subSelection,
                        IOrmSessionImplementor session);

    <T extends IOrmEntity> List<T> findPageByExample(ShardSelection shard, T example,
                                                     List<OrderFieldBean> orderBy, long offset, int limit,
                                                     IOrmSessionImplementor session);

    <T extends IOrmEntity> List<T> findAllByExample(ShardSelection shard, T example,
                                                    List<OrderFieldBean> orderBy, IOrmSessionImplementor session);

    long deleteByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session);

    IOrmEntity findFirstByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session);

    long countByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session);

    long updateByExample(ShardSelection shard, IOrmEntity example, IOrmEntity updated, IOrmSessionImplementor session);
}