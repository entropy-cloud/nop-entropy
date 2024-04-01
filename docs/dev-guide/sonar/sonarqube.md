# 配置

1. 修改maven的settings文件

```xml
    <profile>
      <id>sonar</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <sonar.host.url>http://localhost:9000</sonar.host.url>
        <sonar.token>xxx</sonar.token>
        <sonar.scm.provider>git</sonar.scm.provider>
      </properties>
    </profile>
```

2. 修改工程根目录的pom文件

```xml

<pom>
    <properties>
        <sonar.coverage.jacoco.xmlReportPaths>
            ${maven.multiModuleProjectDirectory}/tests/target/site/jacoco-aggregate/jacoco.xml
        </sonar.coverage.jacoco.xmlReportPaths>
        <sonar.language>java</sonar.language>
    </properties>

    <build>
        <plugins>
            <!-- mvn surefire-report:report指令生成测试报告 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.2</version>

                <configuration>
                    <argLine>
                        ${jacocoArgLine}
                    </argLine>
                    <testFailureIgnore>false</testFailureIgnore>
                    <forkCount>4</forkCount>
                    <reuseForks>true</reuseForks>
                </configuration>

            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>coverage</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.jacoco</groupId>
                        <artifactId>jacoco-maven-plugin</artifactId>
                        <version>0.8.11</version>
                        <executions>
                            <execution>
                                <id>default-prepare-agent</id>
                                <goals>
                                    <goal>prepare-agent</goal>
                                </goals>
                                <!-- 与surefire共用时需要重命名argLine -->
                                <configuration>
                                    <propertyName>jacocoArgLine</propertyName>
                                </configuration>
                            </execution>
                        </executions>
                        <configuration>
                            <excludes>
                                <exclude>**/_gen/*.*</exclude>
                                <exclude>**/*Errors.*</exclude>
                                <exclude>**/*Configs.*</exclude>
                                <exclude>**/*Constants*</exclude>
                                <exclude>**/parse/antlr/*.*</exclude>
                            </excludes>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</pom>
```
