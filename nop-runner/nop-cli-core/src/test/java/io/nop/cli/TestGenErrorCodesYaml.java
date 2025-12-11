package io.nop.cli;

import io.nop.core.exceptions.ErrorCodeExtracter;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestGenErrorCodesYaml extends BaseTestCase {
    @Test
    public void genErrorCodes() {
        File file = new File(getSrcDir(),"main/resources/_vfs/i18n/zh-CN/nop-cli-errors.i18n.yaml");
        ErrorCodeExtracter.INSTANCE.saveToFile(file,
                io.nop.api.core.ApiErrors.class,
                io.nop.core.CoreErrors.class,
                io.nop.excel.ExcelErrors.class,
                io.nop.task.TaskErrors.class,
                io.nop.shell.ShellErrors.class,
                io.nop.match.MatchErrors.class,
                io.nop.javaparser.JavaParserErrors.class,
                io.nop.auth.api.AuthApiErrors.class,
                io.nop.graphql.core.GraphQLErrors.class,
                io.nop.cli.CliErrors.class,
                io.nop.rpc.api.RpcErrors.class,
                io.nop.rpc.model.RpcModelErrors.class,
                io.nop.rpc.core.RpcErrors.class,
                io.nop.socket.SocketErrors.class,
                io.nop.orm.model.OrmModelErrors.class,
                io.nop.report.pdf.ReportPdfErrors.class,
                io.nop.report.core.XptErrors.class,
                io.nop.http.api.HttpApiErrors.class,
                io.nop.orm.eql.OrmEqlErrors.class,
                io.nop.orm.OrmErrors.class,
                io.nop.dao.DaoErrors.class,
                io.nop.xlang.XLangErrors.class,
                io.nop.javac.JavaCompilerErrors.class,
                io.nop.markdown.MarkdownErrors.class,
                io.nop.integration.api.IntegrationErrors.class,
                io.nop.core.CoreErrors.class,
                io.nop.record_mapping.RecordMappingErrors.class,
                io.nop.dataset.DataSetErrors.class,
                io.nop.codegen.CodeGenErrors.class,
                io.nop.commons.CommonErrors.class,
                io.nop.antlr4.tool.AntlrToolErrors.class,
                io.nop.ooxml.xlsx.XlsxErrors.class,
                io.nop.ooxml.common.OfficeErrors.class,
                io.nop.ooxml.docx.DocxErrors.class,
                io.nop.excel.ExcelErrors.class,
                io.nop.web.page.WebPageErrors.class,
                io.nop.converter.DocConvertErrors.class,
                io.nop.xui.XuiErrors.class,
                io.nop.xui.vue.VueErrors.class,
                io.nop.web.WebErrors.class,
                io.nop.antlr4.common.AntlrErrors.class,
                io.nop.record.RecordErrors.class,
                io.nop.ioc.IocErrors.class,
                io.nop.log.core.LogErrors.class,
                io.nop.config.ConfigErrors.class,
                io.nop.dbtool.exp.DbToolExpErrors.class,
                io.nop.ai.core.AiCoreErrors.class,
                io.nop.ai.coder.AiCoderErrors.class,
                io.nop.batch.dsl.BatchDslErrors.class,
                io.nop.batch.dao.NopBatchDaoErrors.class,
                io.nop.batch.gen.BatchGenErrors.class,
                io.nop.batch.core.BatchErrors.class,
                io.nop.autotest.core.AutoTestErrors.class
        );
    }
}
