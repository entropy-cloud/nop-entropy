import org.jetbrains.intellij.platform.gradle.TestFrameworkType

group = "io.github.entropy-cloud"
version = "1.0-SNAPSHOT"

// 注意，Gradle JVM 只能指定为 JDK 21，高于或低于此版本均不可行，Gradle 会自行下载 JDK 21
plugins {
    id("java")
    // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm
    id("org.jetbrains.kotlin.jvm") version "2.4.0"

    id("org.jetbrains.intellij.platform") version "2.16.0"
}

dependencies {
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.1.3")

        bundledPlugins("com.intellij.java", "com.intellij.gradle", "org.jetbrains.plugins.yaml")

        testFramework(TestFrameworkType.Platform)
    }

    // ANTLR 适配器：https://github.com/antlr/antlr4-intellij-adaptor
    implementation("org.antlr:antlr4-intellij-adaptor:0.1")

    implementation("io.github.entropy-cloud:nop-markdown-ext:2.0.0-SNAPSHOT")
    implementation("io.github.entropy-cloud:nop-markdown:2.0.0-SNAPSHOT")
    implementation("io.github.entropy-cloud:nop-xlang-debugger:2.0.0-SNAPSHOT")
    implementation("io.github.entropy-cloud:nop-xlang:2.0.0-SNAPSHOT") {
        //exclude antlr4's dependency icu4j since it is not necessary and is too large.
        exclude(group = "com.ibm.icu")
    }

    testImplementation("junit:junit:4.13.2")
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("262.*")
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    compileTestJava {
        options.encoding = "UTF-8"
    }

    javadoc {
        options.encoding = "UTF-8"
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()

    intellijPlatform {
        defaultRepositories()
    }
}
