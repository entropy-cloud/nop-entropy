set GRAALVM_HOME=C:\Software\graalvm
set JAVA_HOME=%GRAALVM_HOME%
set Path=%GRAALVM_HOME%\bin;%Path%
"C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat" && mvn package -Pnative
