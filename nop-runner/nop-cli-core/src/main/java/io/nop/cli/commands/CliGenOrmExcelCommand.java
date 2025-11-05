/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.schema.SchemaToOrmModel;
import io.nop.orm.pdm.PdmModelParser;
import io.nop.xlang.api.AbstractEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "gen-orm-excel",
    mixinStandardHelpOptions = true,
    description = "Generate Excel ORM model from pdm or pdma.json model file"
)
public class CliGenOrmExcelCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file (default: print to console)")
    File outputFile;


    @CommandLine.Option(names = {"-d", "--dump"}, description = "Dump intermediate info to console")
    boolean dump;

    @CommandLine.Parameters(description = "Model file (.pdm | .pdma.json | .xmeta | .xdef)", index = "0")
    File file;

    @Override
    public Integer call() {
        OrmModel model;
        String type;
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
        } else if (file.getName().endsWith(".xmeta") || file.getName().endsWith(".xdef")) {
            IObjMeta objMeta = SchemaLoader.parseXMetaFromResource(new FileResource(file));
            if (objMeta == null)
                throw new IllegalArgumentException("null meta");
            type = "meta";
            model = new SchemaToOrmModel().transform(objMeta.getDefines());
        } else {
            System.err.println("Only .pdm or .pdma.json model files are supported");
            return -1;
        }

        File outputFile = this.outputFile;
        if (outputFile == null) {
            String fileName = StringHelper.fileFullName(file.getName());
            fileName = StringHelper.firstPart(fileName, '.') + '-' + type;
            outputFile = new File(fileName + ".orm.xlsx");
        }

        GenOrmHelper.saveOrmToExcel(model, outputFile, dump);
        return 0;
    }
}