package io.nop.ai.code_analyzer.code;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.nop.ai.code_analyzer.maven.MavenDependency;
import io.nop.ai.code_analyzer.maven.MavenDependencyNode;
import io.nop.ai.code_analyzer.maven.MavenModule;
import io.nop.ai.code_analyzer.maven.MavenRepository;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaParserBuilder {
    private final CombinedTypeSolver solver = new CombinedTypeSolver();
    private final Map<String, TypeSolver> jarSolvers = new HashMap<>();

    private MavenRepository mavenRepository = MavenRepository.getDefault();

    public TypeSolver getTypeSolver() {
        return solver;
    }

    public JavaParserBuilder repoDir(File repoDir) {
        this.mavenRepository = new MavenRepository(repoDir);
        return this;
    }

    public JavaParser build() {
        // 创建符号解析器
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(getTypeSolver());

        // 配置Parser
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(symbolSolver)
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

        return new JavaParser(config);
    }

    public JavaParserBuilder addReflection() {
        solver.add(new ReflectionTypeSolver());
        return this;
    }

    public JavaParserBuilder addClassPath() {
        solver.add(new ClassLoaderTypeSolver(JavaParserBuilder.class.getClassLoader()));
        return this;
    }

    public JavaParserBuilder addJar(File jarFile) {
        String path = FileHelper.getAbsolutePath(jarFile);
        if (jarSolvers.containsKey(path))
            return this;

        try {
            TypeSolver jarSolver = new JarTypeSolver(jarFile);
            solver.add(jarSolver);
            jarSolvers.put(path, jarSolver);
            return this;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public JavaParserBuilder addModuleJars(MavenModule module) {
        return addDependencyNode(module.getModuleNode());
    }

    public JavaParserBuilder addDependencyNode(MavenDependencyNode depNode) {
        List<MavenDependency> deps = depNode.getAllDependencies(null, true);
        for (MavenDependency dep : deps) {
            File file = mavenRepository.getMavenDependencyFile(dep);
            if (file.isFile()) {
                if (file.getName().endsWith(".jar")) {
                    addJar(file);
                }
            }
        }
        return this;
    }
}
