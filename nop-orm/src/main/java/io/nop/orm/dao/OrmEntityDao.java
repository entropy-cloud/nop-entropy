/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.api.core.util.Guard;
import io.nop.commons.collections.ListFunctions;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IEntityDaoExtension;
import io.nop.dao.exceptions.UnknownEntityException;
import io.nop.orm.IOrmBatchLoadQueue;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmEntityState;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.orm.OrmErrors.ARG_DAO_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_ENTITY;
import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_DAO_PROP_NOT_TO_ONE_RELATION;
import static io.nop.orm.OrmErrors.ERR_ORM_DAO_ENTITY_NAME_NOT_FOR_DAO;
import static io.nop.orm.OrmErrors.ERR_ORM_UPDATE_ENTITY_NOT_MANAGED;
import static io.nop.orm.OrmErrors.ERR_ORM_UPDATE_ENTITY_NO_CURRENT_SESSION;

public class OrmEntityDao<T extends IOrmEntity> implements IOrmEntityDao<T> {
    private IOrmTemplate ormTemplate;
    private String entityName;
    private IDaoProvider daoProvider;

    public OrmEntityDao() {
    }

    public OrmEntityDao(IDaoProvider daoProvider, IOrmTemplate ormTemplate, String entityName) {
        this.ormTemplate = Guard.notNull(ormTemplate, "ormTemplate");
        this.entityName = Guard.notEmpty(entityName, "entityName");
        this.daoProvider = Guard.notNull(daoProvider, "daoProvider");
    }

    @Override
    public IOrmTemplate getOrmTemplate() {
        return ormTemplate;
    }

    protected IOrmTemplate orm() {
        return ormTemplate;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public IEntityModel getEntityModel() {
        return ormTemplate.getOrmModel().requireEntityModel(entityName);
    }

    @PostConstruct
    public void init() {
        getEntityModel();
    }

    @Override
    public String getDeleteFlagProp() {
        return getEntityModel().getDeleteFlagProp();
    }

    @Override
    public String getDeleteVersionProp() {
        return getEntityModel().getDeleteVersionProp();
    }

    @Override
    public boolean isUseLogicalDelete() {
        return getEntityModel().isUseLogicalDelete();
    }

    @Override
    public boolean isUseTenant() {
        return getEntityModel().isUseTenant();
    }

    @Override
    public String getEntityName() {
        return entityName;
    }

    @Override
    public String getTableName() {
        return getEntityModel().getTableName();
    }

    @Override
    public List<String> getPkColumnNames() {
        return getEntityModel().getPkColumnNames();
    }

    @Override
    public String getEntityClassName() {
        return getEntityModel().getClassName();
    }

    @Override
    public Object castId(Object id) {
        return orm().castId(getEntityName(), id);
    }

    @Override
    public List<Object> castIdList(Collection<?> ids) {
        return orm().castIds(getEntityName(), ids);
    }

    void checkEntityNameMatch(T entity) {
        String entityName = entity.orm_entityName();
        if (!entityName.equals(this.getEntityName()))
            throw new NopException(ERR_ORM_DAO_ENTITY_NAME_NOT_FOR_DAO).param(ARG_ENTITY_NAME, entityName)
                    .param(ARG_DAO_ENTITY_NAME, getEntityName());
    }

    @Override
    public void resetToDefaultValues(T entity) {
        IEntityModel entityModel = getEntityModel();
        for (IColumnModel col : entityModel.getColumns()) {
            if (col.isPrimary())
                continue;
            entity.orm_propValue(col.getPropId(), col.getDefaultValue());
        }
    }

    @Override
    public Object initEntityId(T entity) {
        checkEntityNameMatch(entity);
        return orm().initEntityId(entity);
    }

    @Override
    public Object getEntityId(T entity) {
        checkEntityNameMatch(entity);
        return entity.get_id();
    }

    @Override
    public T newEntity() {
        return (T) orm().newEntity(getEntityName());
    }

    @Override
    public void saveEntity(T entity) {
        checkEntityNameMatch(entity);
        orm().save(entity);
    }

    @Override
    public void updateEntity(T entity) {
        checkEntityNameMatch(entity);
        IOrmSession session = orm().currentSession();
        if (session == null)
            throw newEntityError(ERR_ORM_UPDATE_ENTITY_NO_CURRENT_SESSION, entity);

        if (entity.orm_state() == OrmEntityState.PROXY)
            return;

        if (entity.orm_state() != OrmEntityState.MANAGED)
            throw newEntityError(ERR_ORM_UPDATE_ENTITY_NOT_MANAGED, entity);

        orm().saveOrUpdate(entity);
    }

    NopException newEntityError(ErrorCode errorCode, T entity) {
        throw new NopException(errorCode).param(ARG_ENTITY_NAME, entity.orm_entityName())
                .param(ARG_ENTITY_ID, entity.get_id()).param(ARG_ENTITY, entity);
    }

    @Override
    public void saveOrUpdateEntity(T entity) {
        checkEntityNameMatch(entity);
        orm().saveOrUpdate(entity);
    }

    @Override
    public void deleteEntity(T entity) {
        checkEntityNameMatch(entity);
        orm().delete(entity);
    }

    @Override
    public void saveEntityDirectly(T entity) {
        checkEntityNameMatch(entity);
        orm().saveDirectly(entity);
    }

    @Override
    public void updateEntityDirectly(T entity) {
        checkEntityNameMatch(entity);
        orm().updateDirectly(entity);
    }

    @Override
    public void deleteEntityDirectly(T entity) {
        checkEntityNameMatch(entity);
        orm().deleteDirectly(entity);
    }

    @Override
    public T loadEntityById(Object id) {
        if (StringHelper.isEmptyObject(id))
            return null;
        return (T) orm().load(getEntityName(), id);
    }

    @Override
    public void lockEntity(T entity) throws UnknownEntityException {
        checkEntityNameMatch(entity);
        orm().lock(entity);
    }

    @Override
    public T getEntityById(Object id) {
        if (StringHelper.isEmptyObject(id))
            return null;
        return (T) orm().get(getEntityName(), id);
    }

    @Override
    public List<T> batchGetEntitiesByIds(Collection<?> ids) {
        if (ids == null || ids.isEmpty())
            return Collections.emptyList();

        if (ids.size() == 1) {
            Object id = ids.iterator().next();
            if (id == null)
                return Collections.emptyList();
            T entity = loadEntityById(id);
            // force load
            getEntityById(id);
            return Collections.singletonList(entity);
        }

        List<T> ret = this.orm().runInSession(session -> {
            String entityName = getEntityName();
            List<T> list = new ArrayList<>(ids.size());
            IOrmBatchLoadQueue queue = session.getBatchLoadQueue();
            for (Object id : ids) {
                if (id == null)
                    continue;
                T entity = (T) session.load(entityName, id);
                queue.enqueue(entity);
                list.add(entity);
            }
            queue.flush();
            return list;
        });
        return ret;
    }

    @Override
    public List<T> batchRequireEntitiesByIds(Collection<?> ids) {
        List<T> ret = batchGetEntitiesByIds(ids);
        if (ret.isEmpty())
            return ret;
        for (T entity : ret) {
            if (entity.orm_state() == OrmEntityState.MISSING)
                throw new UnknownEntityException(entity.orm_entityName(), entity.get_id());
        }
        return ret;
    }

    @Override
    public List<T> tryBatchGetEntitiesByIds(Collection<?> ids) {
        List<T> ret = batchGetEntitiesByIds(ids);
        if (ret.isEmpty())
            return ret;
        return ret.stream().filter(entity -> !entity.orm_state().isMissing()).collect(Collectors.toList());
    }

    @Override
    public Map<Object, T> batchGetEntityMapByIds(Collection<?> ids) {
        List<T> ret = batchGetEntitiesByIds(ids);
        if (ret.isEmpty())
            return Collections.emptyMap();
        Map<Object, T> map = new HashMap<>();

        for (T entity : ret) {
            if (entity.orm_state() == OrmEntityState.MISSING)
                continue;
            map.put(entity.get_id(), entity);
        }
        return map;
    }

    @Override
    public void attachEntity(T entity) {
        checkEntityNameMatch(entity);
        orm().attach(entity);
    }

    @Override
    public void batchFlush(Collection<T> entities) {
        orm().runInSession(session -> {
            for (T entity : entities) {
                checkEntityNameMatch(entity);
                session.saveOrUpdate(entity);
            }
            return null;
        });
    }

    @Override
    public void batchSaveEntities(Collection<T> entities) {
        orm().runInSession(session -> {
            for (T entity : entities) {
                checkEntityNameMatch(entity);
                session.save(entity);
            }
            return null;
        });
    }

    @Override
    public void batchUpdateEntities(Collection<T> entities) {
        orm().runInSession(session -> {
            for (T entity : entities) {
                checkEntityNameMatch(entity);

                if (entity.orm_state() == OrmEntityState.PROXY)
                    continue;

                if (entity.orm_state() != OrmEntityState.MANAGED)
                    throw newEntityError(ERR_ORM_UPDATE_ENTITY_NOT_MANAGED, entity);

                session.saveOrUpdate(entity);
            }
            return null;
        });
    }

    @Override
    public void batchDeleteEntities(Collection<T> entities) {
        orm().runInSession(session -> {
            for (T entity : entities) {
                checkEntityNameMatch(entity);
                session.delete(entity);
            }
            return null;
        });
    }

    @Override
    public void batchGetEntities(Collection<T> entities) {
        orm().runInSession(session -> {
            IOrmBatchLoadQueue queue = session.getBatchLoadQueue();
            for (T entity : entities) {
                checkEntityNameMatch(entity);
                queue.enqueue(entity);
            }
            queue.flush();
            return null;
        });
    }

    @Override
    public long deleteByExample(T example) {
        checkEntityNameMatch(example);
        return orm().runInSession(session -> session.deleteByExample(example));
    }

    @Override
    public T findFirstByExample(T example) {
        checkEntityNameMatch(example);
        return orm().runInSession(session -> session.findFirstByExample(example));
    }

    @Override
    public boolean isEmpty() {
        return orm().runInSession(session -> session.findFirstByExample(newEntity()) == null);
    }

    @Override
    public long countByExample(T example) {
        checkEntityNameMatch(example);
        return orm().runInSession(session -> session.countByExample(example));
    }

    @Override
    public List<T> findPageByExample(T example, List<OrderFieldBean> orderBy, long offset, int limit) {
        checkEntityNameMatch(example);
        return orm().runInSession(session -> session.findPageByExample(example, orderBy, offset, limit));
    }

    @Override
    public List<T> findAllByExample(T example, List<OrderFieldBean> orderBy) {
        checkEntityNameMatch(example);
        return orm().runInSession(session -> session.findAllByExample(example, orderBy));
    }

    IEntityDaoExtension<T> getDaoQueryExtension() {
        return (IEntityDaoExtension<T>) orm().getExtension(getEntityName(), IEntityDaoExtension.class);
    }

    @Override
    public T findFirstByQuery(QueryBean query) {
        return orm().runInSession(session -> {
            IEntityDaoExtension<T> extension = getDaoQueryExtension();
            if (extension != null && extension.supportReadQuery(query)) {
                return extension.findFirst(query);
            }

            SQL sql = queryToSelectSql(query);
            return orm().findFirst(sql);
        });
    }

    SQL queryToSelectSql(QueryBean query) {
        return DaoQueryHelper.queryToSelectObjectSql(getEntityName(), query);
    }

    SQL queryToCountSql(QueryBean query) {
        return DaoQueryHelper.queryToCountSql(getEntityName(), query);
    }

    SQL queryToDeleteSql(QueryBean query) {
        return DaoQueryHelper.queryToDeleteSql(getEntityName(), query);
    }

    SQL queryToUpdateSql(QueryBean query, Map<String, Object> props) {
        return DaoQueryHelper.queryToUpdateSql(getEntityName(), query, props);
    }

    SQL queryToFindNextSql(T lastEntity, ITreeBean filter, List<OrderFieldBean> orderBy) {
        return DaoQueryHelper.queryToFindNextSql(getEntityModel(), lastEntity, filter, orderBy,
                getEntityModel().getDeleteFlagProp());
    }

    SQL queryToFindPrevSql(T cursorEntity, ITreeBean filter, List<OrderFieldBean> orderBy) {
        return DaoQueryHelper.queryToFindPrevSql(getEntityModel(), cursorEntity, filter, orderBy,
                getEntityModel().getDeleteFlagProp());
    }

    @Override
    public long countByQuery(QueryBean query) {
        return orm().runInSession(session -> {
            IEntityDaoExtension<T> extension = getDaoQueryExtension();
            if (extension != null && extension.supportReadQuery(query)) {
                return extension.count(query);
            }

            SQL sql = queryToCountSql(query);
            return orm().findLong(sql, 0L);
        });
    }

    @Override
    public long deleteByQuery(QueryBean query) {
        return orm().runInSession(session -> {

            IEntityDaoExtension<T> extension = getDaoQueryExtension();
            if (extension != null && extension.supportDeleteQuery(query)) {
                return extension.delete(query);
            }

            SQL sql = queryToDeleteSql(query);
            return orm().executeUpdate(sql);
        });
    }

    @Override
    public boolean existsByQuery(QueryBean query) {
        return orm().runInSession(session -> {
            IEntityDaoExtension<T> extension = getDaoQueryExtension();
            if (extension != null && extension.supportReadQuery(query)) {
                return extension.exists(query);
            }

            SQL sql = queryToSelectSql(query);
            return orm().exists(sql);
        });
    }

    @Override
    public void findPageAndReturnCursor(QueryBean query, PageBean<T> page) {
        T lastEntity = loadEntityByCursor(query.getCursor());

        List<T> list;
        boolean hasPrev, hasNext;
        if (query.isFindPrev()) {
            list = findPrev(lastEntity, query.getFilter(), query.getOrderBy(), query.getLimit() + 1);
            list.remove(list.size() - 1);
            list = ListFunctions.reverse(list);
            hasPrev = list.size() > query.getLimit();
            hasNext = lastEntity != null;
        } else {
            list = findNext(lastEntity, query.getFilter(), query.getOrderBy(), query.getLimit() + 1);
            hasNext = list.size() > query.getLimit();
            list.remove(list.size() - 1);
            hasPrev = lastEntity != null;
        }

        page.setHasNext(hasNext);
        page.setHasPrev(hasPrev);
        if (hasPrev) {
            page.setPrevCursor(list.get(0).orm_idString());
        } else {
            page.setPrevCursor(OrmConstants.ID_NULL);
        }

        if (hasNext) {
            page.setNextCursor(list.get(list.size() - 1).orm_idString());
        } else {
            page.setNextCursor(OrmConstants.ID_NULL);
        }
        page.setItems(list);
    }

    @Override
    public List<T> findPageByQuery(QueryBean query) {
        if (!StringHelper.isEmpty(query.getCursor()) || query.isFindPrev()) {
            if (query.isFindPrev()) {
                return findPrev(query);
            } else {
                return findNext(query);
            }
        } else {
            return _findPageByQuery(query);
        }
    }

    public T loadEntityByCursor(String cursor) {
        if (OrmConstants.ID_NULL.equals(cursor))
            return null;
        return loadEntityById(cursor);
    }

    private List<T> _findPageByQuery(QueryBean query) {
        return orm().runInSession(session -> {
            IEntityDaoExtension<T> extension = getDaoQueryExtension();
            if (extension != null && extension.supportReadQuery(query)) {
                return extension.findPage(query);
            }

            long offset = query.getOffset();
            int limit = query.getLimit();
            SQL sql = queryToSelectSql(query);
            return orm().findPage(sql, offset, limit);
        });
    }

    @Override
    public List<T> findAllByQuery(QueryBean query) {
        return orm().runInSession(session -> {
            IEntityDaoExtension<T> extension = getDaoQueryExtension();
            if (extension != null && extension.supportReadQuery(query)) {
                return extension.findAll(query);
            }

            SQL sql = queryToSelectSql(query);
            return orm().findAll(sql);
        });
    }

    @Override
    public long updateByQuery(QueryBean query, Map<String, Object> props) {
        return orm().runInSession(session -> {
            IEntityDaoExtension<T> extension = getDaoQueryExtension();
            if (extension != null && extension.supportUpdateQuery(query)) {
                return extension.update(query, props);
            }

            SQL sql = queryToUpdateSql(query, props);
            return orm().executeUpdate(sql);
        });
    }

    @Override
    public List<T> findAll() {
        return findAllByExample(newEntity(), null);
    }

    @Override
    public List<T> findNext(T lastEntity, ITreeBean filter, List<OrderFieldBean> orderBy, int limit) {
        return orm().runInSession(session -> {
            IEntityDaoExtension<T> extension = getDaoQueryExtension();
            if (extension != null && extension.supportFindNext(filter, orderBy)) {
                return extension.findNext(lastEntity, filter, orderBy, limit);
            }

            SQL sql = queryToFindNextSql(lastEntity, filter, orderBy);
            return orm().findPage(sql, 0, limit);
        });
    }

    @Override
    public List<T> findPrev(T lastEntity, ITreeBean filter, List<OrderFieldBean> orderBy, int limit) {
        return orm().runInSession(session -> {
            IEntityDaoExtension<T> extension = getDaoQueryExtension();
            if (extension != null && extension.supportFindNext(filter, orderBy)) {
                return extension.findPrev(lastEntity, filter, orderBy, limit);
            }

            SQL sql = queryToFindPrevSql(lastEntity, filter, orderBy);
            return orm().findPage(sql, 0, limit);
        });
    }

    @Override
    public <R extends IDaoEntity> IEntityDao<R> propDao(String propName) {
        IEntityPropModel propModel = _getPropModel(getEntityModel(), propName, false);
        if (propModel == null)
            throw new NopException(ERR_DAO_PROP_NOT_TO_ONE_RELATION).param(ARG_ENTITY_NAME, this.getEntityName())
                    .param(ARG_PROP_NAME, propName);

        String propEntityName;
        if (propModel.isRelationModel()) {
            propEntityName = ((IEntityRelationModel) propModel).getRefEntityModel().getName();
        } else {
            throw new NopException(ERR_DAO_PROP_NOT_TO_ONE_RELATION).param(ARG_ENTITY_NAME, this.getEntityName())
                    .param(ARG_PROP_NAME, propName);
        }

        return daoProvider.dao(propEntityName);
    }

    private IEntityPropModel _getPropModel(IEntityModel model, String propName, boolean ignoreUnknown) {
        if (propName.indexOf('.') < 0) {
            if (OrmConstants.PROP_ID.equals(propName))
                return model.getIdProp();
            IEntityPropModel propModel = model.getProp(propName, ignoreUnknown);
            return propModel;
        } else {
            int pos = propName.indexOf('.');
            String firstPart = propName.substring(0, pos);
            IEntityPropModel propModel = model.getProp(firstPart, ignoreUnknown);
            if (propModel == null) {
                return null;
            }

            if (!propModel.isToOneRelation()) {
                throw new NopException(ERR_DAO_PROP_NOT_TO_ONE_RELATION).param(ARG_PROP_NAME, firstPart)
                        .param(ARG_ENTITY_NAME, model.getName());
            }

            return _getPropModel(((IEntityRelationModel) propModel).getRefEntityModel(), propName.substring(pos + 1),
                    ignoreUnknown);
        }
    }

    @Override
    public void batchLoadProps(Collection<T> entities, Collection<String> propNames) {
        orm().batchLoadProps(entities, propNames);
    }

    @Override
    public void batchLoadSelection(Collection<T> entities, FieldSelectionBean selectionBean) {
        orm().batchLoadSelection(entities, selectionBean);
    }

    @Override
    public void flushSession() {
        orm().flushSession();
    }

    @Override
    public void clearEntitySessionCache() {
        orm().evictAll(getEntityName());
    }

    @Override
    public void clearEntityGlobalCache() {
        orm().clearGlobalCacheFor(getEntityName());
    }

    @Override
    public IEstimatedClock getDbEstimatedClock() {
        return orm().getDbEstimatedClock(getEntityModel().getQuerySpace());
    }
}