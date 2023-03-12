/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.xpl.impl.XplModelParser;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import static io.nop.cli.CliErrors.ARG_NAME;
import static io.nop.cli.CliErrors.ARG_PATH;
import static io.nop.cli.CliErrors.ERR_CLI_UNKNOWN_SCRIPT;

@CommandLine.Command(
        name = "run",
        mixinStandardHelpOptions = true,
        description = "运行脚本"
)
public class CliRunCommand implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "脚本名称")
    String name;

    public Integer call() {
        IResource resource = ResourceHelper.resolveRelativePathResource("scripts/" + name + ".xrun");
        if (!resource.exists())
            throw new NopException(ERR_CLI_UNKNOWN_SCRIPT)
                    .param(ARG_NAME, name)
                    .param(ARG_PATH, resource.getPath());

        XplModelParser parser = new XplModelParser().outputModel(XLangOutputMode.none);
        XplModel xpl = parser.parseFromResource(resource);
        Object result = xpl.invoke(XLang.newEvalScope());
        if (result instanceof Integer)
            return (Integer) result;
        return 0;
    }
}