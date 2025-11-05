/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
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

import static io.nop.orm.eql.OrmEqlErrors.ARG_SQL;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_MULTIPLE_STATEMENT;

public class EqlCompiler implements ISqlCompiler {

    @Override
    public ICompiledSql compile(String name, String eql, ISqlCompileContext context) {
        SqlProgram program = new EqlASTParser().parseFromText(null, eql);
        if (program.getStatements().size() > 1)
            throw new NopException(ERR_EQL_NOT_SUPPORT_MULTIPLE_STATEMENT).param(ARG_SQL, eql);

        SqlStatement stm = program.getStatements().get(0);
        boolean dump = stm.hasDecorator(OrmEqlConstants.DECORATOR_DUMP);
        if (dump) {
            stm.dump("parsed_eql");
        }

        boolean enableFilter = context.isEnableFilter() || stm.hasDecorator(OrmEqlConstants.DECORATOR_ENABLE_FILTER);

        IEqlAstTransformer astTransformer = context.getAstTransformer();

        if (astTransformer != null) {
            astTransformer.transformBeforeAnalyze(program, name, eql, context);
        }

        EqlTransformVisitor transformer = new EqlTransformVisitor(context);
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
                SqlExprProjection exprProj = (SqlExprProjection) proj;
                String fieldName = EqlHelper.getFieldName(exprProj);

                ISqlExprMeta exprMeta = exprProj.getExpr().getResolvedExprMeta();
                Guard.notNull(exprMeta, "fieldMeta");
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
