/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli.commands;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.orm.model.OrmModel;
import io.nop.orm.pdm.PdmModelParser;
import io.nop.xlang.api.AbstractEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelParser;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "gen-orm-excel",
        mixinStandardHelpOptions = true,
        description = "读取pdm模型或者pdma.json模型，生成Excel数据模型"
)
public class CliGenOrmExcelDbCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出文件（缺省输出到命令行窗口中）")
    File outputFile;

    @CommandLine.Parameters(description = "模型文件", index = "0")
    File file;

    @Override
    public Integer call() {
        OrmModel model = null;
        String type = null;
        if (file.getName().endsWith(".pdm")) {
            type = "pdm";
            model = new PdmModelParser().parseFromResource(new FileResource(file));
        } else if (file.getName().endsWith(".pdma.json")) {
            type = "pdman";
            Map<String, Object> json = JsonTool.parseBeanFromResource(new FileResource(file), Map.class);
            AbstractEvalAction action = XLang.getTagAction("/nop/orm/xlib/pdman.xlib", "GenOrmFromJson");
            Map<String, Object> args = new HashMap<>();
            args.put("json", json);
            args.put("versionCol", "REVISION");
            args.put("createrCol", "CREATED_BY");
            args.put("createTime", "CREATED_TIME");
            args.put("updaterCol", "UPDATED_BY");
            args.put("updateTime", "UPDATED_TIME");
            args.put("tenantCol", "TENANT_ID");
            String text = action.generateText(XLang.newEvalScope(args));

            XNode node = XNodeParser.instance().parseFromText(null, text);
            node.setAttr("x:schema", "/nop/schema/orm/orm.xdef");
            node.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");
            model = (OrmModel) new DslModelParser().parseFromNode(node);
        } else {
            System.err.println("只支持pdm或者pmda.json模型文件");
            return -1;
        }

        File outputFile = this.outputFile;
        if (outputFile == null) {
            String fileName = StringHelper.fileFullName(file.getName());
            fileName = StringHelper.firstPart(fileName, '.') + '-' + type;
            outputFile = new File(fileName + ".orm.xlsx");
        }

        GenOrmHelper.saveOrmToExcel(model, outputFile);
        return 0;
    }
}