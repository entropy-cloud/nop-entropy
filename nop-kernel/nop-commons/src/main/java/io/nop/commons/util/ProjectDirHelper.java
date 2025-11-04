/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.util;

// refactor from quarkus project: BuildToolHelper

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper class used to expose build tool used by the project
 */
public class ProjectDirHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectDirHelper.class);

//    private final static String[] DEVMODE_REQUIRED_TASKS = new String[]{"classes"};
//    private final static String[] TEST_REQUIRED_TASKS = new String[]{"classes", "testClasses"};
//    private final static List<String> ENABLE_JAR_PACKAGING = Collections
//            .singletonList("-Dorg.gradle.java.compile-classpath-packaging=true");

    public enum BuildTool {
        MAVEN("pom.xml"), GRADLE("build.gradle", "build.gradle.kts");

        private final String[] buildFiles;

        BuildTool(String... buildFile) {
            this.buildFiles = buildFile;
        }

        public String[] getBuildFiles() {
            return buildFiles;
        }

        public boolean exists(Path root) {
            for (String buildFile : buildFiles) {
                if (Files.exists(root.resolve(buildFile))) {
                    return true;
                }
            }
            return false;
        }
    }

    private ProjectDirHelper() {

    }

    public static Path getProjectDir(Path p) {
        Path currentPath = p;
        while (currentPath != null) {
            if (BuildTool.MAVEN.exists(currentPath) || BuildTool.GRADLE.exists(currentPath)) {
                return currentPath;
            }
            currentPath = currentPath.getParent();
        }
        LOG.warn("Unable to find a project directory for {}.", p);
        return null;
    }

    public static BuildTool findBuildTool(Path project) {
        Path currentPath = project;
        while (currentPath != null) {
            if (BuildTool.MAVEN.exists(currentPath)) {
                return BuildTool.MAVEN;
            }
            if (BuildTool.GRADLE.exists(currentPath)) {
                return BuildTool.GRADLE;
            }
            currentPath = currentPath.getParent();
        }
        LOG.warn("Unable to find a build tool in {} or in any parent.", project);
        return null;
    }

    public static boolean isMavenProject(Path project) {
        return findBuildTool(project) == BuildTool.MAVEN;
    }

    public static boolean isGradleProject(Path project) {
        return findBuildTool(project) == BuildTool.GRADLE;
    }

    public static Path getBuildFile(Path project, BuildTool tool) {
        Path currentPath = project;
        while (currentPath != null) {
            if (tool.exists(currentPath)) {
                return currentPath;
            }
            currentPath = currentPath.getParent();
        }
        return null;
    }
}
