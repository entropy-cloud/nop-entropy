/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.enc;

import io.nop.commons.CommonConstants;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.JdbcBatcher;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.OrmConfigs;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.model.OrmModelConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * plan 2318 Phase 5：可检索加密辅助列的存量数据回填工具。
 *
 * <p>对声明了辅助列（ext:enc-assisted / ext:enc-like）的 enc 主列：按主键排序分批读 id+主列密文 →
 * 解密（复用 {@code EncodedDataParameterBinder} 的读语义：剥离 {@code @enc:} 前缀后经
 * {@link ITextCipher#decrypt} 识别 v1/legacy 格式；未带前缀的存量值按明文处理）→ 按契约重算
 * assisted/like 摘要 → 仅批量更新辅助列（不触碰主列与其他列）。</p>
 *
 * <p>契约（design §3.4）：</p>
 * <ul>
 * <li>幂等：重复执行按相同明文重算覆盖，结果一致；</li>
 * <li>主列为 null 的行跳过（辅助列保持原值不动）；</li>
 * <li>分批大小配置 {@code nop.orm.enc-backfill-batch-size}（缺省 500，{@link OrmConfigs}）；
 * 按主键排序 offset 分页，兼容单列/复合主键与各方言；</li>
 * <li>直接走 JDBC，不经 ORM session——辅助列的只读守护与派生逻辑不会被触发，也不影响实体缓存；
 * 建议离线执行（回填期间的业务写入不在本工具的一致性保证范围内）。</li>
 * </ul>
 *
 * <p>解密所用 {@link ITextCipher} 必须与列绑定器增强器
 * （{@code DefaultOrmColumnBinderEnhancer}）配置的密码器一致，缺省同为 {@link AESTextCipher}；
 * 自定义密码器时须同步注入。分片实体仅回填缺省 querySpace（分片多库场景需按 querySpace 分别执行）。</p>
 */
public class EncAssistedBackfiller {
    private static final Logger LOG = LoggerFactory.getLogger(EncAssistedBackfiller.class);

    private final IOrmSessionFactory sessionFactory;
    private ITextCipher textCipher = new AESTextCipher();

    public EncAssistedBackfiller(IOrmSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void setTextCipher(ITextCipher textCipher) {
        this.textCipher = textCipher;
    }

    /**
     * 回填模型中所有声明了辅助列的实体，返回更新的行数。
     */
    public long backfillAll() {
        IOrmModel ormModel = sessionFactory.getOrmModel();
        long total = 0;
        for (IEntityModel entityModel : ormModel.getEntityModels()) {
            total += backfillEntity(entityModel);
        }
        return total;
    }

    /**
     * 回填指定实体，返回更新的行数。未声明辅助列的实体返回 0。
     */
    public long backfillEntity(String entityName) {
        return backfillEntity(sessionFactory.getOrmModel().requireEntityModel(entityName));
    }

    public long backfillEntity(IEntityModel entityModel) {
        List<Binding> bindings = resolveBindings(entityModel);
        if (bindings.isEmpty())
            return 0;

        String querySpace = entityModel.getQuerySpace();
        IDialect dialect = sessionFactory.getDialectForQuerySpace(querySpace);
        IJdbcTemplate jdbc = sessionFactory.getJdbcTemplate();
        int batchSize = requireBatchSize();

        List<? extends IColumnModel> pkCols = entityModel.getPkColumns();
        SQL selectSql = buildSelectSql(dialect, entityModel, pkCols, bindings);

        long updated = 0;
        long offset = 0;
        while (true) {
            List<Object[]> rows = jdbc.findPage(selectSql, offset, batchSize,
                    (row, rowNumber, colMapper) -> row.getFieldValues());
            if (rows.isEmpty())
                break;
            updated += flushBatch(jdbc, dialect, querySpace, entityModel, pkCols, bindings, rows);
            if (rows.size() < batchSize)
                break;
            offset += batchSize;
        }

        LOG.info("nop.orm.enc-backfill.entity:entityName={},updated={},batchSize={}",
                entityModel.getName(), updated, batchSize);
        return updated;
    }

    private int requireBatchSize() {
        int batchSize = OrmConfigs.CFG_ORM_ENC_BACKFILL_BATCH_SIZE.get();
        if (batchSize < 1)
            throw new IllegalArgumentException(
                    "nop.orm.enc-backfill-batch-size must be >= 1, got: " + batchSize);
        return batchSize;
    }

    /**
     * 单实体辅助列绑定：enc 主列 → assisted/like 辅助列（引用合法性已在模型加载期校验）。
     */
    private List<Binding> resolveBindings(IEntityModel entityModel) {
        List<Binding> bindings = new ArrayList<>(2);
        for (IColumnModel col : entityModel.getColumns()) {
            if (!col.containsTag(OrmModelConstants.TAG_ENC))
                continue;
            IColumnModel assisted = resolveHelper(entityModel, col, OrmModelConstants.EXT_ENC_ASSISTED);
            IColumnModel like = resolveHelper(entityModel, col, OrmModelConstants.EXT_ENC_LIKE);
            if (assisted != null || like != null)
                bindings.add(new Binding(col, assisted, like));
        }
        return bindings;
    }

    private IColumnModel resolveHelper(IEntityModel entityModel, IColumnModel master, String extName) {
        Object value = master.prop_get(extName);
        return value == null ? null : entityModel.requireColumn(value.toString());
    }

    private SQL buildSelectSql(IDialect dialect, IEntityModel entityModel, List<? extends IColumnModel> pkCols,
                               List<Binding> bindings) {
        StringBuilder sb = new StringBuilder();
        sb.append("select ");
        for (IColumnModel pk : pkCols) {
            sb.append(dialect.normalizeColumnName(pk.getCode())).append(',');
        }
        for (Binding binding : bindings) {
            sb.append(dialect.normalizeColumnName(binding.master.getCode())).append(',');
        }
        sb.setLength(sb.length() - 1);
        sb.append(" from ").append(dialect.normalizeTableName(entityModel.getTableName()));
        sb.append(" order by ");
        for (IColumnModel pk : pkCols) {
            sb.append(dialect.normalizeColumnName(pk.getCode())).append(',');
        }
        sb.setLength(sb.length() - 1);

        return SQL.begin().querySpace(entityModel.getQuerySpace())
                .name("enc-backfill-select:" + entityModel.getName())
                .sql(sb.toString()).end();
    }

    /**
     * 一个批次的回填：主列为 null 的行跳过；按"非空主列绑定掩码"分组，使同组行共享同一条
     * UPDATE 文本以启用 JDBC batch。返回更新的行数。
     */
    private long flushBatch(IJdbcTemplate jdbc, IDialect dialect, String querySpace, IEntityModel entityModel,
                            List<? extends IColumnModel> pkCols, List<Binding> bindings, List<Object[]> rows) {
        int pkCount = pkCols.size();
        Map<Integer, List<Object[]>> groups = new HashMap<>();
        for (Object[] row : rows) {
            int mask = 0;
            for (int i = 0; i < bindings.size(); i++) {
                if (masterValue(row, pkCount, i) != null)
                    mask |= (1 << i);
            }
            if (mask == 0) {
                LOG.debug("nop.orm.enc-backfill.skip-null-master:entityName={}", entityModel.getName());
                continue;
            }
            groups.computeIfAbsent(mask, k -> new ArrayList<>()).add(row);
        }

        long updated = 0;
        for (Map.Entry<Integer, List<Object[]>> entry : groups.entrySet()) {
            int mask = entry.getKey();
            updated += executeBatch(jdbc, dialect, querySpace, entityModel, pkCols, bindings, mask, entry.getValue());
        }
        return updated;
    }

    private long executeBatch(IJdbcTemplate jdbc, IDialect dialect, String querySpace, IEntityModel entityModel,
                              List<? extends IColumnModel> pkCols, List<Binding> bindings, int mask,
                              List<Object[]> rows) {
        SQL anchor = SQL.begin().querySpace(querySpace)
                .name("enc-backfill-update:" + entityModel.getName()).end();
        return jdbc.runWithConnection(anchor, conn -> {
            JdbcBatcher batcher = new JdbcBatcher(conn, dialect, null);
            long count = 0;
            for (Object[] row : rows) {
                batcher.addCommand(buildRowUpdate(dialect, querySpace, entityModel, pkCols, bindings, mask, row),
                        true, null);
                count++;
            }
            batcher.flush();
            return count;
        });
    }

    /**
     * 构造单行 UPDATE（仅辅助列入 SET，主键定位）。经 builder 的 param 通道绑定参数值，
     * 同掩码组的行产出完全相同的 SQL 文本以启用 JDBC batch。
     */
    private SQL buildRowUpdate(IDialect dialect, String querySpace, IEntityModel entityModel,
                               List<? extends IColumnModel> pkCols, List<Binding> bindings, int mask, Object[] row) {
        int pkCount = pkCols.size();
        SQL.SqlBuilder sb = SQL.begin().querySpace(querySpace)
                .name("enc-backfill-update:" + entityModel.getName())
                .sql("update ").sql(dialect.normalizeTableName(entityModel.getTableName())).sql(" set ");
        EncAssistedHelper helper = EncAssistedHelper.instance();
        boolean hasSet = false;
        for (int i = 0; i < bindings.size(); i++) {
            if ((mask & (1 << i)) == 0)
                continue;
            Binding binding = bindings.get(i);
            String plain = decryptStored((String) masterValue(row, pkCount, i));
            if (binding.assisted != null) {
                if (hasSet)
                    sb.sql(",");
                sb.sql(dialect.normalizeColumnName(binding.assisted.getCode())).sql("=").param(helper.assistedDigest(plain));
                hasSet = true;
            }
            if (binding.like != null) {
                if (hasSet)
                    sb.sql(",");
                sb.sql(dialect.normalizeColumnName(binding.like.getCode())).sql("=").param(helper.likeDigest(plain));
                hasSet = true;
            }
        }
        if (!hasSet)
            throw new IllegalStateException("empty backfill update set: " + entityModel.getName());
        sb.sql(" where ");
        for (int i = 0; i < pkCols.size(); i++) {
            if (i != 0)
                sb.sql(" and ");
            sb.sql(dialect.normalizeColumnName(pkCols.get(i).getCode())).sql("=").param(row[i]);
        }
        return sb.end();
    }

    private Object masterValue(Object[] row, int pkCount, int bindingIndex) {
        return row[pkCount + bindingIndex];
    }

    /**
     * 复用 {@code EncodedDataParameterBinder} 的读语义：带 {@code @enc:} 前缀的值剥离前缀后解密
     * （{@link ITextCipher#decrypt} 自识别 v1/legacy 格式）；未带前缀的存量值原样返回（按明文处理，
     * 不经 decrypt——与 binder 读路径完全一致）。
     */
    private String decryptStored(String stored) {
        if (stored.startsWith(CommonConstants.ENC_VALUE_PREFIX))
            return textCipher.decrypt(stored.substring(CommonConstants.ENC_VALUE_PREFIX.length()));
        return stored;
    }

    private static final class Binding {
        final IColumnModel master;
        final IColumnModel assisted;
        final IColumnModel like;

        Binding(IColumnModel master, IColumnModel assisted, IColumnModel like) {
            this.master = master;
            this.assisted = assisted;
            this.like = like;
        }
    }
}
