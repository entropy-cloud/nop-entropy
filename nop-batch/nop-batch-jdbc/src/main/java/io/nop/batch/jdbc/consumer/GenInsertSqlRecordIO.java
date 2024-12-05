/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.consumer;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dao.DaoConstants;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

public class GenInsertSqlRecordIO implements IResourceRecordIO<Map<String, Object>> {
    private String dialect = DaoConstants.DIALECT_MYSQL;
    private List<String> fields;

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    @Override
    public IRecordInput<Map<String, Object>> openInput(IResource resource, String encoding) {
        throw new UnsupportedOperationException("openInput");
    }

    @Override
    public IRecordOutput<Map<String, Object>> openOutput(IResource resource, String encoding) {
        String tableName = StringHelper.fileNameNoExt(resource.getName());

        IDialect dialect = DialectManager.instance().getDialect(this.dialect);
        return new GenInsertSqlOutput(dialect, tableName, fields, resource.getWriter(null));
    }

    static class GenInsertSqlOutput implements IRecordOutput<Map<String, Object>> {
        private final IDialect dialect;
        private final String tableName;
        private final List<String> fields;
        private final Writer out;

        public GenInsertSqlOutput(IDialect dialect, String tableName, List<String> fields, Writer out) {
            this.dialect = dialect;
            this.tableName = tableName;
            this.fields = fields;
            this.out = out;
        }

        private long count;

        @Override
        public long getWriteCount() {
            return count;
        }

        @Override
        public void write(Map<String, Object> record) throws IOException {
            count++;

            out.write(buildInsertSql(record));
        }

        String buildInsertSql(Map<String, Object> record) {
            StringBuilder sb = new StringBuilder();
            sb.append("insert into ").append(tableName);
            sb.append("(");
            boolean first = true;
            if (fields != null) {
                for (String colName : fields) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(',');
                    }
                    sb.append(colName);
                }
            } else {
                for (String colName : record.keySet()) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(',');
                    }
                    sb.append(colName);
                }
            }
            sb.append(") values (");
            first = true;
            if (fields != null) {
                for (String colName : fields) {
                    Object value = record.get(colName);
                    if (first) {
                        first = false;
                    } else {
                        sb.append(',');
                    }
                    sb.append(encode(value));
                }
            } else {
                for (Object value : record.values()) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(',');
                    }
                    sb.append(encode(value));
                }
            }
            sb.append(")");
            sb.append(";\n");
            return sb.toString();
        }

        private String encode(Object value) {
            return dialect.getValueLiteral(value);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }
}
