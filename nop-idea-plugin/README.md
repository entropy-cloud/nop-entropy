# Nop IDEA Plugin

## 构建

在当前项目的根目录下执行 `gradle buildPlugin` 或 `bash ./gradlew buildPlugin`
指令，最终的构建产物将被打包到 `build/distributions` 目录下。

## 调试

将本项目导入到 IntelliJ IDEA 中，点开 `Gradle` 工具窗口。
依次展开 `Tasks -> intellij`，再选中 `runIde`。
接着，点击右键并选择 `Debug 'nop-idea-plugin [run...'`，
其将启动一个新的 IntelliJ IDEA 实例，并在其中自动安装/更新该插件。

> 新 IDEA 实例的版本与当前插件在 `build.gradle.kts` 中所配置的 `intellij` 的版本一致，
> 首次启动，将会自动下载并安装该版本实例（安装位置如
> `$HOME/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC`）。

后续，在项目代码所在 IDEA 中添加断点，并在新 IDEA 实例中操作，
便可以像调试普通代码一样对当前插件进行调试。
