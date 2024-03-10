/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.consumer;

import io.nop.api.core.exceptions.NopException;
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
import java.util.Map;

public class GenInsertSqlRecordIO implements IResourceRecordIO<Map<String, Object>> {
    private String dialect = DaoConstants.DIALECT_MYSQL;

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    @Override
    public IRecordInput<Map<String, Object>> openInput(IResource resource, String encoding) {
        throw new UnsupportedOperationException("openInput");
    }

    @Override
    public IRecordOutput<Map<String, Object>> openOutput(IResource resource, String encoding) {
        String tableName = StringHelper.fileNameNoExt(resource.getName());

        IDialect dialect = DialectManager.instance().getDialect(this.dialect);
        return new GenInsertSqlOutput(dialect, tableName, resource.getWriter(null));
    }

    static class GenInsertSqlOutput implements IRecordOutput<Map<String, Object>> {
        private final IDialect dialect;
        private final String tableName;
        private final Writer out;

        public GenInsertSqlOutput(IDialect dialect, String tableName, Writer out) {
            this.dialect = dialect;
            this.tableName = tableName;
            this.out = out;
        }

        private long count;

        @Override
        public long getWriteCount() {
            return count;
        }

        @Override
        public void write(Map<String, Object> record) {
            count++;
            try {
                out.write(buildInsertSql(record));
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }

        String buildInsertSql(Map<String, Object> record) {
            StringBuilder sb = new StringBuilder();
            sb.append("insert into ").append(tableName);
            sb.append("(");
            boolean first = true;
            for (String colName : record.keySet()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(',');
                }
                sb.append(colName);
            }
            sb.append(") values (");
            first = true;
            for (Object value : record.values()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(',');
                }
                sb.append(encode(value));
            }
            sb.append(")");
            sb.append(";\n");
            return sb.toString();
        }

        private String encode(Object value) {
            return dialect.getValueLiteral(value);
        }

        @Override
        public void flush() {
            try {
                out.flush();
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }
}
