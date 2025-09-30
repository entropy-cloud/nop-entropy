# Configuration

1. Modify the Maven settings file

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

2. Modify the pom file in the project root directory

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
            <!-- The mvn surefire-report:report command generates a test report -->
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
                                <!-- When used together with Surefire, argLine needs to be renamed -->
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

<!-- SOURCE_MD5:58cc1f87df5f3e5b5b98cc4d08185649-->
