package io.nop.orm.model.lazy;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.graph.TopoEntry;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.init.OrmModelTopEntryBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_OTHER_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_OTHER_LOC;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_DUPLICATE_ENTITY_SHORT_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_ENTITY_NAME;

public class LazyLoadOrmModel implements ILazyLoadOrmModel {
    private final IOrmModel baseModel;
    private final IDynamicEntityModelProvider entityModelLoader;

    private final Map<String, TopoEntry<IEntityModel>> topoEntryMap = new ConcurrentHashMap<>();
    private volatile List<IEntityModel> sortedEntityModels;
    private final Map<String, IEntityModel> entityModelByTableMap = new ConcurrentHashMap<>();
    private final Map<String, IEntityModel> entityModelMap = new ConcurrentHashMap<>();

    private final Map<String, IEntityModel> snakeCaseNameMap = new ConcurrentHashMap<>();
    private volatile boolean anyEntityUseTenant;

    private volatile boolean topoEntryInited = false;

    public LazyLoadOrmModel(IOrmModel baseModel, IDynamicEntityModelProvider entityModelLoader) {
        this.baseModel = baseModel;
        this.anyEntityUseTenant = baseModel != null && baseModel.isAnyEntityUseTenant();
        this.entityModelLoader = entityModelLoader;
    }

    @Override
    public boolean isAnyEntityUseTenant() {
        return anyEntityUseTenant;
    }

    protected void checkTopoEntryReady() {
        if (this.topoEntryInited)
            return;

        synchronized (this.entityModelMap) {
            if (topoEntryInited)
                return;


            List<IEntityModel> entityModels = new ArrayList<>();
            if (baseModel != null)
                entityModels.addAll(baseModel.getEntityModels());
            entityModels.addAll(entityModelMap.values());
            new OrmModelTopEntryBuilder().build(entityModels, topoEntryMap);

            Map<TopoEntry<IEntityModel>, IEntityModel> map = new TreeMap<>();
            topoEntryMap.values().forEach(entry -> {
                map.put(entry, entry.getValue());
            });
            this.sortedEntityModels = map.values().stream().collect(Collectors.toUnmodifiableList());
            topoEntryInited = true;
        }
    }

    protected IEntityModel loadEntityModel(String entityName) {
        return entityModelLoader.getDynamicEntityModel(entityName, this);
    }

    @Override
    public List<IEntityModel> getEntityModelInTopoOrder(Collection<String> entityNames) {
        checkTopoEntryReady();
        if (entityNames.isEmpty())
            return Collections.emptyList();
        if (entityNames.size() == 1)
            return List.of(requireEntityModel(entityNames.iterator().next()));

        TreeMap<TopoEntry<IEntityModel>, IEntityModel> map = new TreeMap<>();
        entityNames.forEach(name -> {
            TopoEntry<IEntityModel> entry = topoEntryMap.get(name);
            if (entry == null)
                throw new NopException(ERR_ORM_UNKNOWN_ENTITY_NAME).param(ARG_ENTITY_NAME, name);
            map.put(entry, entry.getValue());
        });
        return new ArrayList<>(map.values());
    }

    @Override
    public List<IEntityModel> sortEntityModelInTopoOrder(Collection<IEntityModel> entityModels) {
        checkTopoEntryReady();
        if (entityModels.isEmpty())
            return Collections.emptyList();
        if (entityModels.size() == 1)
            return List.of(entityModels.iterator().next());

        TreeMap<TopoEntry<IEntityModel>, IEntityModel> map = new TreeMap<>();
        entityModels.forEach(entityModel -> {
            String name = entityModel.getName();
            TopoEntry<IEntityModel> entry = topoEntryMap.get(name);
            if (entry == null)
                throw new NopException(ERR_ORM_UNKNOWN_ENTITY_NAME).param(ARG_ENTITY_NAME, name);
            map.put(entry, entry.getValue());
        });
        return new ArrayList<>(map.values());
    }

    @Override
    public Collection<? extends IEntityModel> getEntityModelsInTopoOrder() {
        return getEntityModels();
    }

    @Override
    public List<? extends IEntityModel> getEntityModels() {
        checkTopoEntryReady();
        return sortedEntityModels;
    }

    @Override
    public Set<String> getEntityNames() {
        checkTopoEntryReady();
        return topoEntryMap.keySet();
    }

    @Override
    public IEntityModel getEntityModel(String entityName) {
        if (baseModel != null) {
            IEntityModel entityModel = baseModel.getEntityModel(entityName);
            if (entityModel != null)
                return entityModel;
        }
        IEntityModel entityModel = entityModelMap.get(entityName);
        if (entityModel != null)
            return entityModel;

        synchronized (entityModelMap) {
            entityModel = entityModelMap.get(entityName);
            if (entityModel != null)
                return entityModel;
        }

        // 2.3-C2-018（plan 2553 裁定 E）：DB+codegen 加载移出 entityModelMap 同步块——原形态
        // 锁内慢 I/O 串行化所有并发 getEntityModel。per-key CF 去重（并发同 key 共享单次加载）；
        // 重入守卫（R1-Major-3）：ThreadLocal 在飞 key 集，IDynamicEntityModelProvider 加载体
        // 回环 getEntityModel 时 fail-fast（否则 CF.join 自死锁无监视器可救）。
        CompletableFuture<IEntityModel> loadFuture = inFlightLoads.computeIfAbsent(entityName, k -> {
            try {
                return CompletableFuture.supplyAsync(() -> {
                    // 在飞 key 集记在执行加载的线程上（reentrant 守卫的判定域）
                    IN_FLIGHT_KEYS.get().add(k);
                    try {
                        return loadEntityModel(k);
                    } finally {
                        IN_FLIGHT_KEYS.get().remove(k);
                    }
                });
            } catch (Throwable t) {
                inFlightLoads.remove(k);
                throw t;
            }
        });
        if (loadFuture != null && !IN_FLIGHT_KEYS.get().isEmpty()
                && inFlightLoads.containsKey(entityName)) {
            // 2.3-C2-018 重入守卫（执行期补强，R1-Major-3）：加载线程回环 getEntityModel 时
            // computeIfAbsent 命中既有 future、mapping 守卫不触发——若放行将 CF.get 自等待
            //（同 key 自死锁；跨 key 环 A→B→A 为跨线程等待环，同样无监视器可救）。
            // 判定：当前线程自身在任一加载中（IN_FLIGHT_KEYS 非空）且请求 key 仍在飞 → 回环，
            // fail-fast。执行期载体：TestLazyLoadConcurrentLoad pin_c。
            throw new IllegalStateException("circular dynamic entity load detected: " + entityName);
        }

        IEntityModel loaded;
        try {
            loaded = loadFuture.get();
        } catch (java.util.concurrent.ExecutionException e) {
            throw NopException.adapt(e.getCause());
        } catch (java.lang.InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        } finally {
            inFlightLoads.remove(entityName, loadFuture);
        }
        if (loaded == null)
            return null;

        synchronized (entityModelMap) {
            // 重新进入锁域做 put + 短名预检（复用 2552 裁定 K 预检逻辑；putEntityModel 自身
            // 重获锁，拓扑协议不破坏——putEntityModel 内部 synchronized(entityModelMap)）
            putEntityModel(loaded);
        }
        return entityModelMap.get(entityName);
    }

    /** 2.3-C2-018：per-key 在飞加载去重 */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<IEntityModel>> inFlightLoads =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 2.3-C2-018 重入守卫：加载线程的在飞 key 集（fail-fast on 回环） */
    private static final ThreadLocal<java.util.Set<String>> IN_FLIGHT_KEYS =
            ThreadLocal.withInitial(java.util.HashSet::new);

    @Override
    public void addEntityModel(IEntityModel entityModel) {
        putEntityModel(entityModel);
    }

    /**
     * 必须与 {@link #checkTopoEntryReady()} 在同一把锁（entityModelMap）内执行，
     * 否则并发的 put 与重建之间没有 happens-before 约束：当 put 的 topoEntryInited=false
     * 落在重建过程中间时，最终状态为 topoEntryInited=true 但拓扑表中不包含新实体，且不会再触发重建
     */
    void putEntityModel(IEntityModel entityModel) {
        synchronized (this.entityModelMap) {
            // 2.3-C1-018（plan 2552 裁定 K）：只读预检短名冲突——先检后写（预检-写入间隙由本锁
            // 覆盖，99abdf8f28 落地的机制锁面），消除「先写 table/name 索引再因短名冲突抛出」
            // 的半注册残留面；同名重复注册静默覆盖 name 键的第二形态=范围外（plan 2552 closure 注记）。
            if (entityModel.isRegisterShortName()) {
                IEntityModel oldModel = entityModelMap.get(entityModel.getShortName());
                if (oldModel != null && oldModel != entityModel)
                    throw new NopException(ERR_ORM_MODEL_DUPLICATE_ENTITY_SHORT_NAME).source(entityModel)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_OTHER_LOC, oldModel.getLocation())
                            .param(ARG_OTHER_ENTITY_NAME, oldModel.getName());
            }

            entityModelByTableMap.put(normalizeTableName(entityModel.getTableName()), entityModel);

            entityModelMap.put(entityModel.getName(), entityModel);
            if (entityModel.isRegisterShortName()) {
                entityModelMap.put(entityModel.getShortName(), entityModel);
            }

            if (entityModel.isRegisterShortName()) {
                // 只有entityModel的短名字不重复的情况下才支持underscore名称，否则可能会出现重名的问题
                String underscoreName = StringHelper.camelCaseToUnderscore(entityModel.getShortName(), true);
                if (underscoreName.equals(entityModel.getTableName()))
                    underscoreName = entityModel.getTableName();
                // 只支持全大写和全小写
                snakeCaseNameMap.put(underscoreName, entityModel);
                snakeCaseNameMap.put(underscoreName.toUpperCase(Locale.ROOT), entityModel);
            }
            this.topoEntryInited = false;
        }
    }

    /**
     * 与静态 OrmModel 的 CaseInsensitiveMap 索引保持一致：表名查找忽略大小写。
     * ConcurrentHashMap 自行按归一化后的 key 存取
     */
    private static String normalizeTableName(String tableName) {
        return tableName.toLowerCase(Locale.ROOT);
    }

    @Override
    public IEntityModel getEntityModelByTableName(String tableName) {
        if (baseModel != null) {
            IEntityModel entityModel = baseModel.getEntityModelByTableName(tableName);
            if (entityModel != null)
                return entityModel;
        }
        return entityModelByTableMap.get(normalizeTableName(tableName));
    }

    @Override
    public IEntityModel getEntityModelBySnakeCaseName(String name) {
        if (baseModel != null) {
            IEntityModel entityModel = baseModel.getEntityModelBySnakeCaseName(name);
            if (entityModel != null)
                return entityModel;
        }
        return snakeCaseNameMap.get(name);
    }

    @Override
    public IEntityRelationModel getCollectionModel(String collectionName) {
        if (baseModel != null) {
            IEntityRelationModel relModel = baseModel.getCollectionModel(collectionName);
            if (relModel != null)
                return relModel;
        }

        int pos = collectionName.indexOf('@');
        // 与静态 OrmModel 的 map 查找语义对齐：名字非法时返回 null，
        // 由 requireCollectionModel 统一抛 ERR_ORM_UNKNOWN_COLLECTION_NAME
        if (pos <= 0)
            return null;
        String entityName = collectionName.substring(0, pos);
        IEntityModel entityModel = requireEntityModel(entityName);

        String propName = collectionName.substring(pos + 1);
        return entityModel.getRelation(propName, true);
    }

    @Override
    public List<DictBean> getDicts() {
        if (baseModel != null)
            return baseModel.getDicts();
        return null;
    }

    @Override
    public DictBean getDict(String name) {
        if (baseModel != null)
            return baseModel.getDict(name);
        return null;
    }

    @Override
    public Object prop_get(String propName) {
        if (baseModel != null)
            return baseModel.prop_get(propName);
        return null;
    }

    @Override
    public boolean prop_has(String propName) {
        if (baseModel != null)
            return baseModel.prop_has(propName);
        return false;
    }
}
