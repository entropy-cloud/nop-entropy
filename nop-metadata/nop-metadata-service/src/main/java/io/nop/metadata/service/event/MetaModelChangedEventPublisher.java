package io.nop.metadata.service.event;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaModelChangedEvent;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 元数据变更事件发布 helper（架构基线 §2.8 / 设计 10 / plan 2026-07-17-0228-1，D2 发布机制主路径）。
 *
 * <p>无状态 service 层 IoC bean（{@code @Inject IDaoProvider}），由写路径（关键 mutation action + 核心实体
 * save/delete override）在持久化**成功后**调用，构造一行 {@link NopMetaModelChangedEvent} 并 {@code saveEntity} 持久化。
 *
 * <p>设计决策：
 * <ul>
 *   <li>主路径 = 直接 DB 写入事件行（不依赖 IMessageService 订阅者注册机制，首版无订阅者）。</li>
 *   <li>快照序列化用 {@link JsonTool}（before/after 为实体 JSON，从实体模型列构建 Map 后序列化）。</li>
 *   <li>失败路径（如快照序列化异常）**显式抛 inline ErrorCode**（不静默吞掉、不静默跳过事件发布不留痕迹）。</li>
 *   <li>不伪造缺失快照：调用方按事件类型传入 before/after（ENTITY_CREATED 仅 after、ENTITY_UPDATED before+after、
 *       ENTITY_DELETED 仅 before）。</li>
 *   <li><b>AR-07 凭据脱敏（核心）</b>：从 ORM column model 的 tagSet 读 "sensitive" 标记，
 *       对标记列返回固定 {@link #REDACTED_VALUE}（不读取实际值）；同时维护 {@link #SENSITIVE_COLUMN_FALLBACK}
 *       硬编码列名兜底集（即使 ORM tagSet 缺失，常见凭据列仍兜底脱敏，defense-in-depth）。</li>
 * </ul>
 *
 * <p>本类不自造连接、不复制持久化逻辑（{@code saveEntity} 走标准 {@link IEntityDao}）。
 */
public class MetaModelChangedEventPublisher {

    static final ErrorCode ERR_EVENT_SNAPSHOT_SERIALIZE_FAILED =
            ErrorCode.define("metadata.event-snapshot-serialize-failed",
                    "Failed to serialize change-event snapshot: entityType={entityType} entityId={entityId} error={error}",
                    "entityType", "entityId", "error");

    /**
     * 变更来源常量（plain string + 文档约定，首版不 dict 化，对齐 dimension-type/granularity 模式）。
     * 定义在此非生成类中（不写入生成的 _NopMetadataCoreConstants，避免被 codegen 覆盖）。
     */
    public static final String CHANGE_SOURCE_IMPORT = "IMPORT";
    public static final String CHANGE_SOURCE_UI = "UI";
    public static final String CHANGE_SOURCE_API = "API";
    public static final String CHANGE_SOURCE_SYNC = "SYNC";

    /**
     * AR-07: 敏感列固定脱敏占位值（不读取实际值；调用方/审计可见"该列存在但被脱敏"）。
     */
    public static final String REDACTED_VALUE = "***REDACTED***";

    /**
     * AR-07: tagSet 标记名，标识敏感列。在 nop-metadata.orm.xml 的 column 上配置 tagSet="sensitive"。
     */
    public static final String SENSITIVE_TAG = "sensitive";

    /**
     * AR-07 defense-in-depth：硬编码兜底列名集合。即使 ORM 模型未配置 tagSet=sensitive（如外部实体），
     * 常见凭据列名仍兜底脱敏，防止因元数据缺失导致凭据落盘。
     */
    private static final Set<String> SENSITIVE_COLUMN_FALLBACK = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "connectionConfig", "connection_config",
                    "password", "passwd", "pwd",
                    "secret", "apiKey", "api_key", "accessKey", "access_key",
                    "privateKey", "private_key",
                    "token", "accessToken", "access_token",
                    "jdbcUrl", "jdbc_url")));

    private final IDaoProvider daoProvider;

    @Inject
    public MetaModelChangedEventPublisher(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    /**
     * 发布一条元数据变更事件（持久化一行 {@link NopMetaModelChangedEvent}）。
     *
     * <p>调用方须在业务写（super.save/super.delete/mutation action 的核心持久化）**成功后**调用本方法，
     * 避免业务写失败/事务回滚时产生幽灵事件。
     *
     * @param eventType     ENTITY_CREATED | ENTITY_UPDATED | ENTITY_DELETED
     * @param entityType    实体类型（NopMetaModule / NopMetaTable / NopMetaDataSource / ...）
     * @param entityId      变更实体 ID
     * @param entityName    变更实体名称（便于日志，nullable）
     * @param changeSource  IMPORT | UI | API | SYNC
     * @param before        变更前实体（用于 beforeSnapshot 序列化），nullable（CREATE 时为 null）
     * @param after         变更后实体（用于 afterSnapshot 序列化），nullable（DELETE 时为 null）
     * @param transactionId 批次/单操作 correlation key（nullable）
     * @param context       服务上下文（取 changedBy；nullable 时记 null）
     * @return 已持久化的 NopMetaModelChangedEvent
     */
    public NopMetaModelChangedEvent publishEvent(String eventType, String entityType, String entityId,
                                                 String entityName, String changeSource,
                                                 Object before, Object after, String transactionId,
                                                 IServiceContext context) {
        return publishEventWithSnapshots(eventType, entityType, entityId, entityName, changeSource,
                buildSnapshot(before, entityType, entityId), buildSnapshot(after, entityType, entityId),
                transactionId, context);
    }

    /**
     * 发布事件（接受已构建的快照 JSON 字符串）。用于 UPDATE 路径 where before 必须在变更前预先捕获
     * （如 mutation action 内对同一实体对象就地修改 before 无法在持久化后还原）。
     *
     * <p>失败路径（saveEntity 持久化）由调用方的事务边界保护；快照已在 {@link #buildSnapshot} 时校验。
     */
    public NopMetaModelChangedEvent publishEventWithSnapshots(String eventType, String entityType, String entityId,
                                                              String entityName, String changeSource,
                                                              String beforeSnapshot, String afterSnapshot,
                                                              String transactionId, IServiceContext context) {
        IEntityDao<NopMetaModelChangedEvent> eventDao = daoProvider.daoFor(NopMetaModelChangedEvent.class);
        NopMetaModelChangedEvent event = eventDao.newEntity();
        event.setEventType(eventType);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setEntityName(entityName);
        event.setChangeSource(changeSource);
        event.setBeforeSnapshot(beforeSnapshot);
        event.setAfterSnapshot(afterSnapshot);
        event.setChangedBy(context != null ? context.getUserId() : null);
        event.setChangeTime(new Timestamp(System.currentTimeMillis()));
        event.setTransactionId(transactionId);
        eventDao.saveEntity(event);
        return event;
    }

    /**
     * 生成单个操作的 transactionId（per-op UUID，便于未来关联同操作的多事件扩展）。
     */
    public static String newTransactionId() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * 将实体序列化为 JSON 快照（公共方法，供调用方在变更前预先捕获 before 快照）。
     * null 实体返回 null（CREATE 无 before、DELETE 无 after）。
     *
     * <p>从实体模型列构建有序 Map（避免 ORM 内部状态混入快照），再 {@link JsonTool#stringify}。
     * 序列化失败显式抛 {@link #ERR_EVENT_SNAPSHOT_SERIALIZE_FAILED}（不静默吞掉）。
     *
     * <p><b>AR-07</b>：对标记为 sensitive 的列返回 {@link #REDACTED_VALUE}（不读取实际值），
     * 防止凭据在事件表里二次落盘。
     */
    public String buildSnapshot(Object entity, String entityType, String entityId) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> snapshot = buildEntitySnapshot(entity);
        try {
            return JsonTool.stringify(snapshot);
        } catch (Throwable e) {
            // 序列化任意对象可能产生 StackOverflowError（循环引用）等 Error；快照边界须显式收口为 ErrorCode，
            // 不静默吞掉、不静默跳过事件发布（架构基线 §2.8 失败路径显式）。
            throw new NopException(ERR_EVENT_SNAPSHOT_SERIALIZE_FAILED)
                    .param("entityType", entityType)
                    .param("entityId", entityId)
                    .param("error", e.toString());
        }
    }


    /**
     * 从实体模型列构建快照 Map。非 ORM 实体时回退为 stringify→parse 取 Map。
     *
     * <p>AR-07：对 sensitive 列（经 ORM tagSet 标记或硬编码兜底）返回固定 {@link #REDACTED_VALUE}。
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> buildEntitySnapshot(Object entity) {
        if (entity instanceof io.nop.orm.IOrmEntity) {
            io.nop.orm.IOrmEntity ormEntity = (io.nop.orm.IOrmEntity) entity;
            IEntityModel model = ormEntity.orm_entityModel();
            Map<String, Object> map = new LinkedHashMap<>();
            if (model != null) {
                for (IColumnModel col : model.getColumns()) {
                    String name = col.getName();
                    // AR-07: sensitive 列脱敏（不读取实际值）
                    if (isSensitiveColumn(col, name)) {
                        map.put(name, REDACTED_VALUE);
                        continue;
                    }
                    Object value = ormEntity.orm_propValueByName(name);
                    if (value instanceof Date) {
                        value = ((Date) value).getTime();
                    }
                    map.put(name, value);
                }
            }
            return map;
        }
        if (entity instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) entity);
        }
        // 非 ORM 实体（POJO）：stringify→parse 取 Map（防御路径，本 helper 实际只接收 ORM 实体）
        Object parsed = JsonTool.parse(JsonTool.stringify(entity));
        return parsed instanceof Map ? (Map<String, Object>) parsed : new LinkedHashMap<>();
    }

    /**
     * AR-07: 判定列是否敏感。优先读 ORM tagSet（runtime 配置驱动），fallback 硬编码列名集（defense-in-depth）。
     */
    static boolean isSensitiveColumn(IColumnModel col, String name) {
        if (col != null) {
            Set<String> tagSet = col.getTagSet();
            if (tagSet != null && tagSet.contains(SENSITIVE_TAG)) {
                return true;
            }
        }
        if (name == null) {
            return false;
        }
        return SENSITIVE_COLUMN_FALLBACK.contains(name);
    }
}
