package io.nop.orm.persister;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.commons.collections.IntArray;
import io.nop.orm.IOrmEntity;
import io.nop.orm.id.IEntityIdGenerator;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.session.IOrmSessionImplementor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 负责处理全局缓存、shard选择、逻辑删除、batchSize拆分等逻辑，内部调用{@link io.nop.orm.driver.IEntityPersistDriver}来具体负责实际的数据存储
 */
public interface IEntityPersister extends AutoCloseable {
    <T> T getExtension(Class<T> clazz);

    void init(IEntityModel entityModel, IEntityIdGenerator idGenerator,
              IPersistEnv env);

    IEntityModel getEntityModel();

    void generateId(IOrmEntity entity);

    /**
     * 新建的实体对象entityModel和session属性已经被初始化
     *
     * @param session
     * @return
     */
    IOrmEntity newEntity(IOrmSessionImplementor session);

    /**
     * 支持GraphQL，采用按需加载模式
     *
     * @param entity  实体对象
     * @param propIds 需要加载的实体属性列表
     * @param session 当前session
     * @return 加载成功返回true
     */
    CompletionStage<Boolean> loadAsync(IOrmEntity entity, IntArray propIds, IOrmSessionImplementor session);

    boolean lock(IOrmEntity entity, IntArray propIds, IOrmSessionImplementor session, Runnable unlockCallback);

    CompletionStage<Void> batchLoadAsync(Collection<IOrmEntity> entities, IntArray propIds,
                                         FieldSelectionBean subSelection, IOrmSessionImplementor session);

    void save(IOrmEntity entity, IOrmSessionImplementor session);

    void update(IOrmEntity entity, IOrmSessionImplementor session);

    void delete(IOrmEntity entity, IOrmSessionImplementor session);

    CompletionStage<Void> batchExecuteAsync(boolean topoAsc, String querySpace, List<IBatchAction.EntitySaveAction> saveActions,
                                            List<IBatchAction.EntityUpdateAction> updateActions,
                                            List<IBatchAction.EntityDeleteAction> deleteActions, IOrmSessionImplementor session);

    <T extends IOrmEntity> List<T> findPageByExample(T example, List<OrderFieldBean> orderBy, long offset, int limit,
                                                     IOrmSessionImplementor session);

    <T extends IOrmEntity> List<T> findAllByExample(T example, List<OrderFieldBean> orderBy,
                                                    IOrmSessionImplementor session);

    long deleteByExample(IOrmEntity example, IOrmSessionImplementor session);

    IOrmEntity findFirstByExample(IOrmEntity example, IOrmSessionImplementor session);

    long countByExample(IOrmEntity example, IOrmSessionImplementor session);

    long updateByExample(IOrmEntity example, IOrmEntity updated, IOrmSessionImplementor session);
}