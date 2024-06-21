package io.nop.codegen;

import io.nop.commons.util.MavenDirHelper;
import io.nop.core.initialize.CoreInitialization;

import java.io.File;

public class CodeGenForIoC {
    public static void main(String[] args) {
        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(CodeGenForIoC.class);
            // new GenAopProxy().execute(new File(projectDir,"../nop-graphql/nop-graphql-core"),false);
            XCodeGenerator.runPrecompile(new File(projectDir, "../nop-ioc"), "", false);
        } finally {
            CoreInitialization.destroy();
        }
    }
}
