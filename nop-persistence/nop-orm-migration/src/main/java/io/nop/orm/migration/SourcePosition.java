/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.orm.migration.MigrationErrors.ERR_MIG_P0_PIN_FAILED;
import static io.nop.orm.migration.MigrationErrors.ARG_DETAIL;
import static io.nop.orm.migration.MigrationErrors.ARG_DIALECT;

/**
 * PREPARE 钉定的源位点 P0（设计 §4.2 位点时序契约）：mysql binlog file+pos（可选 GTID 集合）/
 * pg WAL LSN。FULL 启动之前钉定，INCREMENTAL 从 P0 起全序重放——收敛性由此结构性成立。
 *
 * <p>JSON 形态持久化于编排状态行，并可被 {@code CdcOffsetSeedBuilder} 转换为 Debezium
 * offset 种子。</p>
 */
public class SourcePosition {
    public static final String TYPE_MYSQL = "mysql";
    public static final String TYPE_POSTGRES = "postgres";

    private String connectorType;
    private String binlogFile;
    private Long binlogPos;
    private String gtidSet;
    private String lsn;

    public static SourcePosition mysql(String binlogFile, long binlogPos, String gtidSet) {
        SourcePosition p = new SourcePosition();
        p.connectorType = TYPE_MYSQL;
        p.binlogFile = binlogFile;
        p.binlogPos = binlogPos;
        p.gtidSet = gtidSet;
        return p;
    }

    public static SourcePosition postgres(String lsn) {
        SourcePosition p = new SourcePosition();
        p.connectorType = TYPE_POSTGRES;
        p.lsn = lsn;
        return p;
    }

    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("connectorType", connectorType);
        if (binlogFile != null)
            map.put("binlogFile", binlogFile);
        if (binlogPos != null)
            map.put("binlogPos", binlogPos);
        if (gtidSet != null)
            map.put("gtidSet", gtidSet);
        if (lsn != null)
            map.put("lsn", lsn);
        return JSON.stringify(map);
    }

    @SuppressWarnings("unchecked")
    public static SourcePosition fromJson(String json) {
        if (json == null || json.isEmpty())
            throw new NopException(ERR_MIG_P0_PIN_FAILED).param(ARG_DIALECT, "unknown")
                    .param(ARG_DETAIL, "position P0 is empty");
        Object obj;
        try {
            obj = JSON.parseNonStrict(json);
        } catch (Exception e) {
            throw new NopException(ERR_MIG_P0_PIN_FAILED, e).param(ARG_DIALECT, "unknown")
                    .param(ARG_DETAIL, "position P0 json parse error: " + e.getMessage());
        }
        Map<String, Object> map = (Map<String, Object>) obj;
        SourcePosition p = new SourcePosition();
        p.connectorType = (String) map.get("connectorType");
        p.binlogFile = (String) map.get("binlogFile");
        Object pos = map.get("binlogPos");
        p.binlogPos = pos == null ? null : Long.valueOf(pos.toString());
        p.gtidSet = (String) map.get("gtidSet");
        p.lsn = (String) map.get("lsn");
        if (p.connectorType == null)
            throw new NopException(ERR_MIG_P0_PIN_FAILED).param(ARG_DIALECT, "unknown")
                    .param(ARG_DETAIL, "position P0 json missing connectorType");
        return p;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public String getBinlogFile() {
        return binlogFile;
    }

    public Long getBinlogPos() {
        return binlogPos;
    }

    public String getGtidSet() {
        return gtidSet;
    }

    public String getLsn() {
        return lsn;
    }
}
