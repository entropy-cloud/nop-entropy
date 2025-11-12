set GRAALVM_HOME=C:\software\graalvm
set JAVA_HOME=%GRAALVM_HOME%
set Path=%GRAALVM_HOME%\bin;%Path%

call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat"
mvn native:compile -DskipTests -Pnative
