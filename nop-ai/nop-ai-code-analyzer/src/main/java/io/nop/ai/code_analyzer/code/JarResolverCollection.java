package io.nop.ai.code_analyzer.code;

import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import io.nop.ai.code_analyzer.maven.MavenDependency;
import io.nop.ai.code_analyzer.maven.MavenDependencyNode;
import io.nop.ai.code_analyzer.maven.MavenModule;
import io.nop.ai.code_analyzer.maven.MavenRepository;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JarResolverCollection {
    private final Map<String, TypeSolver> jarSolvers = new HashMap<>();

    private MavenRepository mavenRepository = MavenRepository.getDefault();

    public JarResolverCollection repoDir(File repoDir) {
        this.mavenRepository = new MavenRepository(repoDir);
        return this;
    }

    public TypeSolver getTypeSolver(File jarFile) {
        String path = FileHelper.getAbsolutePath(jarFile);
        return jarSolvers.get(path);
    }

    public TypeSolver addJar(File jarFile) {
        String path = FileHelper.getAbsolutePath(jarFile);
        if (jarSolvers.containsKey(path))
            return jarSolvers.get(path);

        try {
            TypeSolver jarSolver = new JarTypeSolver(jarFile);
            jarSolvers.put(path, jarSolver);
            return jarSolver;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public Set<TypeSolver> addModuleJars(MavenModule module) {
        return addDependencyNode(module.getModuleNode());
    }

    public Set<TypeSolver> addDependencyNode(MavenDependencyNode depNode) {
        Set<TypeSolver> ret = new LinkedHashSet<>();

        List<MavenDependency> deps = depNode.getAllDependencies(null, true);
        for (MavenDependency dep : deps) {
            File file = mavenRepository.getMavenDependencyFile(dep);
            if (file.isFile()) {
                if (file.getName().endsWith(".jar")) {
                    ret.add(addJar(file));
                }
            }
        }
        return ret;
    }
}
