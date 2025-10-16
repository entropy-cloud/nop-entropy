package io.nop.ai.code_analyzer.code;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.nop.ai.code_analyzer.maven.MavenModule;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class JavaParserBuilder {
    private final CombinedTypeSolver solver = new CombinedTypeSolver();
    private final JarResolverCollection jarFiles;
    private final Set<TypeSolver> jarSolvers = new HashSet<>();

    public JavaParserBuilder(JarResolverCollection jarFiles) {
        this.jarFiles = jarFiles;
    }

    public JavaParserBuilder() {
        this(new JarResolverCollection());
    }

    public TypeSolver getTypeSolver() {
        return solver;
    }

    public JavaParserBuilder repoDir(File repoDir) {
        jarFiles.repoDir(repoDir);
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
        TypeSolver jarSolver = jarFiles.addJar(jarFile);
        if (this.jarSolvers.add(jarSolver))
            this.solver.add(jarSolver);
        return this;
    }

    public JavaParserBuilder addModuleJars(MavenModule module) {
        Set<TypeSolver> solvers = jarFiles.addModuleJars(module);
        solvers.forEach(solver -> {
            if (this.jarSolvers.add(solver))
                this.solver.add(solver);
        });
        return this;
    }
}
