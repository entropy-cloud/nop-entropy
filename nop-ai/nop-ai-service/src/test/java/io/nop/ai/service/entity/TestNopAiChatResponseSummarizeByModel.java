package io.nop.ai.service.entity;

import io.nop.ai.dao.dto.ModelUsageSummary;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.ai.service.NopAiErrors.ERR_AI_SESSION_ID_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 203 (L2-20) + plan 204 (L2-19) + plan 2255 focused tests for the per-model
 * aggregation EQL + row mapping exposed by {@link NopAiChatResponseBizModel#summarizeByModel}.
 *
 * <p>执行路径对齐生产（plan 2255 的核心修正）：被测聚合语句经 {@code orm().findAll}（EQL 编译路径，
 * {@link OrmTemplateImpl} + {@link OrmSessionFactoryBean} 真会话工厂）执行，而不是早期版本用
 * {@link IJdbcTemplate} 直连——两条路径对同一段文本的语义不同（EQL 编译 vs 原生 SQL），用
 * jdbcTemplate 执行会掩盖实体名解析失败（曾掩盖 {@code ERR_EQL_UNKNOWN_ENTITY_NAME} 路径错配）。
 * jdbcTemplate 仅用于测试脚手架（建表后的种子数据插入）。
 *
 * <p>建表用 ORM 工厂 DDL（{@code allNullable=true}）：实体模型的 mandatory 列（model_id、version、
 * 审计列等）不生成 NOT NULL，保留 {@code model_id} 故意为 null 的种子行——null-modelId 独立分组
 * + estimatedCost graceful degradation 断言依赖该数据。
 *
 * <p>断言内容不变：SQL GROUP BY + SUM/COUNT 聚合、{@code LEFT JOIN NopAiModel} 定价 join +
 * {@code estimated_cost} 计算（plan 204 / L2-19）、snake_case 别名 → camelCase 行映射端到端。
 */
public class TestNopAiChatResponseSummarizeByModel {

    private SimpleDataSource dataSource;
    private IJdbcTemplate jdbc;
    private OrmSessionFactoryBean factoryBean;
    private IOrmTemplate orm;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:test-summarize-by-model-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        jdbc = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbc);
        factoryBean.setBeanProvider(new IBeanProvider() {
            @Override
            public boolean containsBean(String name) {
                return false;
            }

            @Override
            public <T> T getBeanByType(Class<T> clazz) {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("cannot instantiate " + clazz.getName(), e);
                }
            }

            @Override
            public Object getBean(String name) {
                return null;
            }

            @Override
            public String getBeanScope(String name) {
                return null;
            }
        });
        factoryBean.setGlobalCache(new LocalCacheProvider("summarize-orm-test", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        orm = new OrmTemplateImpl(sessionFactory);

        // allNullable=true：不生成 NOT NULL，null model_id / null 审计列种子行可插入
        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new DdlSqlCreator(jdbc.getDialectForQuerySpace(null)).createTables(tables, true, false);
        jdbc.executeMultiSql(new SQL(createSql));

        // Pricing rows for nop_ai_model: m-x has pricing, m-y has null pricing.
        // No row is inserted for the unresolved null-modelId group, so its LEFT JOIN miss.
        insertModelRow("m-x", "open", "gpt-4", "1.5000", "2.5000", "USD");
        insertModelRow("m-y", "anth", "claude", null, null, null);

        // session-A: model-X x3 + model-Y x2 + null-modelId x1
        insertRow("r1", "session-A", "m-x", "open", "gpt-4", 100, 10, 1000L);
        insertRow("r2", "session-A", "m-x", "open", "gpt-4", 200, 20, 2000L);
        insertRow("r3", "session-A", "m-x", "open", "gpt-4", 300, 30, 3000L);
        insertRow("r4", "session-A", "m-y", "anth", "claude", 50, 5, 500L);
        insertRow("r5", "session-A", "m-y", "anth", "claude", 150, 15, 1500L);
        insertRow("r6", "session-A", null, "unk", "unk-model", 40, 4, 400L);
        // session-B: must be excluded from session-A aggregation
        insertRow("r7", "session-B", "m-x", "open", "gpt-4", 999, 999, 999L);
    }

    @AfterEach
    void tearDown() {
        if (factoryBean != null)
            factoryBean.destroy();
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    @Test
    void multiModelSessionReturnsOneGroupPerModel() {
        List<ModelUsageSummary> result = summarize("session-A");
        assertEquals(3, result.size(),
                "session-A must aggregate into 3 groups (model-X, model-Y, null-modelId)");
    }

    @Test
    void aggregationValuesAreCorrectForModelX() {
        Map<String, ModelUsageSummary> byKey = summarizeByKey("session-A");

        ModelUsageSummary x = byKey.get("open|gpt-4");
        assertNotNull(x, "model-X group must be present");
        assertEquals("m-x", x.getModelId());
        assertEquals(600L, x.getTotalPromptTokens(), "100+200+300");
        assertEquals(60L, x.getTotalCompletionTokens(), "10+20+30");
        assertEquals(3L, x.getCallCount());
        assertEquals(6000L, x.getTotalDurationMs(), "1000+2000+3000");
        // plan 204 / L2-19: estimatedCost now computed via LEFT JOIN nop_ai_model pricing.
        // 600 * 1.5 / 1000000 + 60 * 2.5 / 1000000 = 0.0009 + 0.00015 = 0.00105
        assertNotNull(x.getEstimatedCost(), "estimatedCost must be computed when pricing data is present (L2-19)");
        assertEquals(0, new BigDecimal("0.00105").compareTo(x.getEstimatedCost()),
                "estimatedCost = prompt*inputPrice/1M + completion*outputPrice/1M, actual=" + x.getEstimatedCost());
    }

    @Test
    void aggregationValuesAreCorrectForModelY() {
        Map<String, ModelUsageSummary> byKey = summarizeByKey("session-A");

        ModelUsageSummary y = byKey.get("anth|claude");
        assertNotNull(y, "model-Y group must be present");
        assertEquals("m-y", y.getModelId());
        assertEquals(200L, y.getTotalPromptTokens(), "50+150");
        assertEquals(20L, y.getTotalCompletionTokens(), "5+15");
        assertEquals(2L, y.getCallCount());
        assertEquals(2000L, y.getTotalDurationMs(), "500+1500");
        // m-y row has null pricing → SQL null propagation → estimatedCost null (graceful degradation).
        assertNull(y.getEstimatedCost(),
                "estimatedCost must be null when nop_ai_model pricing columns are null");
    }

    @Test
    void nullModelIdRowsFormTheirOwnGroup() {
        Map<String, ModelUsageSummary> byKey = summarizeByKey("session-A");

        ModelUsageSummary unk = byKey.get("unk|unk-model");
        assertNotNull(unk, "null model_id rows must form an independent group by provider+model");
        assertNull(unk.getModelId(), "model_id stays null for the unresolved-model group");
        assertEquals(40L, unk.getTotalPromptTokens());
        assertEquals(4L, unk.getTotalCompletionTokens());
        assertEquals(1L, unk.getCallCount());
        assertEquals(400L, unk.getTotalDurationMs());
        // No nop_ai_model row to join (model_id is null) → LEFT JOIN miss → estimatedCost null.
        assertNull(unk.getEstimatedCost(),
                "estimatedCost must be null when model_id is null (no nop_ai_model row to join)");
    }

    @Test
    void aggregationIsScopedToTheGivenSession() {
        List<ModelUsageSummary> a = summarize("session-A");
        assertTrue(a.stream().noneMatch(m -> "session-B".equals(m)),
                "session-A aggregation must not include session-B rows");
        // sanity: model-X total for session-A excludes session-B's 999 tokens
        Map<String, ModelUsageSummary> byKey = summarizeByKey("session-A");
        assertEquals(600L, byKey.get("open|gpt-4").getTotalPromptTokens(),
                "session-B row (999 tokens) must not leak into session-A model-X total");
    }

    @Test
    void emptySessionReturnsEmptyList() {
        List<ModelUsageSummary> result = summarize("nonexistent-session");
        assertNotNull(result);
        assertTrue(result.isEmpty(), "a session with no rows must return an empty list");
    }

    @Test
    void blankSessionIdThrowsNopExceptionNotSilentEmpty() {
        NopAiChatResponseBizModel bizModel = new NopAiChatResponseBizModel();
        NopException ex = assertThrows(NopException.class,
                () -> bizModel.summarizeByModel(null, null),
                "null sessionId must fail fast, not silently return empty (Minimum Rules #24)");
        assertEquals(ERR_AI_SESSION_ID_REQUIRED.getErrorCode(), ex.getErrorCode());

        NopException ex2 = assertThrows(NopException.class,
                () -> bizModel.summarizeByModel("   ", null),
                "blank sessionId must fail fast, not silently return empty");
        assertEquals(ERR_AI_SESSION_ID_REQUIRED.getErrorCode(), ex2.getErrorCode());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** 生产同路径：经 orm()（EQL 编译）执行被测语句，在会话内运行。 */
    private List<ModelUsageSummary> summarize(String sessionId) {
        return orm.runInSession(s ->
                orm.findAll(NopAiChatResponseBizModel.buildSummarySql(sessionId),
                        NopAiChatResponseBizModel.ROW_MAPPER));
    }

    private Map<String, ModelUsageSummary> summarizeByKey(String sessionId) {
        Map<String, ModelUsageSummary> map = new HashMap<>();
        for (ModelUsageSummary m : summarize(sessionId)) {
            map.put(m.getAiProvider() + "|" + m.getAiModel(), m);
        }
        return map;
    }

    private void insertRow(String id, String sessionId, String modelId, String provider,
                           String model, int promptTokens, int completionTokens, long durationMs) {
        SQL sql = SQL.begin()
                .append("INSERT INTO nop_ai_chat_response "
                        + "(id, session_id, model_id, ai_provider, ai_model, "
                        + "prompt_tokens, completion_tokens, response_duration_ms) VALUES (")
                .param0(id).append(',')
                .param0(sessionId).append(',')
                .param0(modelId).append(',')
                .param0(provider).append(',')
                .param0(model).append(',')
                .append(promptTokens).append(',')
                .append(completionTokens).append(',')
                .append(durationMs)
                .append(')')
                .end();
        jdbc.executeUpdate(sql);
    }

    /**
     * 插入 {@code nop_ai_model} 行（测试脚手架，jdbcTemplate 直插）。{@code inputPricePer1m} /
     * {@code outputPricePer1m} 传 null 用于验证"定价列缺失 → estimatedCost 为 null"的
     * graceful degradation 路径。
     */
    private void insertModelRow(String id, String provider, String modelName,
                                String inputPricePer1m, String outputPricePer1m, String currency) {
        SQL sql = SQL.begin()
                .append("INSERT INTO nop_ai_model "
                        + "(id, provider, model_name, input_price_per_1m, output_price_per_1m, currency) VALUES (")
                .param0(id).append(',')
                .param0(provider).append(',')
                .param0(modelName).append(',')
                .param0(inputPricePer1m).append(',')
                .param0(outputPricePer1m).append(',')
                .param0(currency)
                .append(')')
                .end();
        jdbc.executeUpdate(sql);
    }
}
