/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.impl.BaseDataFieldMeta;
import io.nop.dataset.impl.BaseDataSetMeta;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.OrmEqlConstants;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlSelect;
import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.SqlStatement;
import io.nop.orm.eql.ast.SqlTransactionStatement;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.parse.EqlASTParser;
import io.nop.orm.eql.sql.AstToSqlGenerator;
import io.nop.orm.eql.utils.EqlHelper;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IOrmDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.orm.eql.OrmEqlErrors.ARG_AST_NODE;
import static io.nop.orm.eql.OrmEqlErrors.ARG_SQL;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_MULTIPLE_STATEMENT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_TRANSACTION_STATEMENT;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNSUPPORTED_PROJECTION_TYPE;

public class EqlCompiler implements ISqlCompiler {

    @Override
    public ICompiledSql compile(String name, String eql, ISqlCompileContext context) {
        SqlProgram program = new EqlASTParser().parseFromText(null, eql);
        if (program.getStatements().size() > 1)
            throw new NopException(ERR_EQL_NOT_SUPPORT_MULTIPLE_STATEMENT).param(ARG_SQL, eql);

        SqlStatement stm = program.getStatements().get(0);

        // Plan 2307 V4: 事务控制语句显式失败。
        // 修复前 commit/rollback 能一路走到输出阶段，而两个输出器都没有它们的 printer，
        // 于是产出**空字符串** SQL 且不报任何错误——一条静默无操作。
        // 事务边界归属 ITransactionTemplate / 连接层（见 design §3.5），
        // 让 EQL 编译器输出裸 COMMIT 也不携带任何事务语义，故在此处 fail-fast。
        // 判定放在 transform 之前：尽早失败，且不产生任何半成品 SQL。
        if (stm instanceof SqlTransactionStatement) {
            throw new NopException(ERR_EQL_NOT_SUPPORT_TRANSACTION_STATEMENT)
                    .source(stm)
                    .param("statementKind", String.valueOf(stm.getStatementKind()));
        }

        boolean dump = stm.hasDecorator(OrmEqlConstants.DECORATOR_DUMP);
        if (dump) {
            stm.dump("parsed_eql");
        }

        boolean enableFilter = context.isEnableFilter() || stm.hasDecorator(OrmEqlConstants.DECORATOR_ENABLE_FILTER);

        IEqlAstTransformer astTransformer = context.getAstTransformer();

        if (astTransformer != null) {
            astTransformer.transformBeforeAnalyze(program, name, eql, context);
        }

        EqlTransformVisitor transformer = new EqlTransformVisitor(context, name);
        transformer.transform(program);

        if (astTransformer != null) {
            astTransformer.transformAfterAnalyze(program, name, eql, context);
        }

        if (dump) {
            stm.dump("transformed-eql");
        }

        AstToSqlGenerator genSql = new AstToSqlGenerator(transformer.getDialect(), enableFilter);
        genSql.setPretty(true);
        genSql.visit(stm);

        String querySpace = transformer.getQuerySpace();
        SQL sql = genSql.getSql().querySpace(querySpace).name("jdbc:" + name).end();

        CompiledSql compiledSql = new CompiledSql();
        compiledSql.setSql(sql);
        compiledSql.setQuerySpace(transformer.getQuerySpace());
        compiledSql.setStatementKind(stm.getStatementKind());
        compiledSql.setName(name);
        compiledSql.setParamBuilders(transformer.getParams());
        compiledSql.setReadEntityModels(transformer.getReadEntityModels());
        compiledSql.setWriteEntityModel(transformer.getWriteEntityModel());
        compiledSql.setUseTenantModel(transformer.isUseTenantModel());

        if (stm.getStatementKind() == SqlStatementKind.SELECT) {
            SqlSelect select = stm instanceof SqlSelectWithCte ? ((SqlSelectWithCte) stm).getSelect() : (SqlSelect) stm;
            List<SqlProjection> projections = select.getProjections();
            List<ISqlExprMeta> fieldMetas = new ArrayList<>(projections.size());
            List<BaseDataFieldMeta> fields = new ArrayList<>(projections.size());
            for (SqlProjection proj : projections) {
                // Plan 2307 V6: 顶层 SELECT 的投影此前是无条件强转。实测常规路径
                // （select * / owner.* / union）都会先被展开为 SqlExprProjection，
                // 故该分支当前不可达；但作为公开编译入口，无条件强转一旦遇到未展开的
                // 投影就会抛 ClassCastException（无错误码）。改为带错误码的显式失败。
                if (!(proj instanceof SqlExprProjection)) {
                    throw new NopException(ERR_EQL_UNSUPPORTED_PROJECTION_TYPE)
                            .source(proj)
                            .param(ARG_AST_NODE, String.valueOf(proj.getASTKind()));
                }
                SqlExprProjection exprProj = (SqlExprProjection) proj;
                String fieldName = EqlHelper.getFieldName(exprProj);

                ISqlExprMeta exprMeta = exprProj.getExpr() != null
                        ? exprProj.getExpr().getResolvedExprMeta() : null;
                if (exprMeta == null) {
                    // Guard.notNull 抛的是无错误码的 IllegalStateException/CCE，
                    // 这里换成带错误码的显式失败，便于上层定位到具体投影。
                    throw new NopException(ERR_EQL_UNSUPPORTED_PROJECTION_TYPE)
                            .source(exprProj)
                            .param(ARG_AST_NODE, String.valueOf(exprProj.getASTKind()));
                }
                fieldMetas.add(exprMeta);

                BaseDataFieldMeta field = getBaseDataFieldMeta(exprMeta, fieldName);
                fields.add(field);
            }
            BaseDataSetMeta meta = new BaseDataSetMeta(fields);
            compiledSql.setDataSetMeta(meta);
            compiledSql.setColumnBinders(collectColumnBinders(fieldMetas));
            compiledSql.setFieldMetas(fieldMetas);
        }
        return compiledSql;
    }

    private static BaseDataFieldMeta getBaseDataFieldMeta(ISqlExprMeta exprMeta, String fieldName) {
        IOrmDataType dataType = exprMeta.getOrmDataType();
        String sourceFieldName = null;
        String ownerEntityName = null;
        if (dataType instanceof IEntityPropModel) {
            IEntityPropModel propModel = (IEntityPropModel) dataType;
            sourceFieldName = propModel.getName();
            ownerEntityName = propModel.getOwnerEntityModel().getName();
        }
        BaseDataFieldMeta field = new BaseDataFieldMeta(fieldName, sourceFieldName, ownerEntityName,
                exprMeta.getStdDataType(), exprMeta.getStdSqlType(), false);
        return field;
    }

    List<IDataParameterBinder> collectColumnBinders(List<ISqlExprMeta> exprs) {
        return exprs.stream().flatMap(expr -> expr.getColumnBinders().stream()).collect(Collectors.toList());
    }
}
