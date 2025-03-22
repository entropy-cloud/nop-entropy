# Configuration

1. Modify Maven's settings file

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

2. Modify the root directory's pom file

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
            <!-- Generate test report using mvn surefire-report:report -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.2</version>
                <configuration>
                    <argLine>${jacocoArgLine}</argLine>
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
                                <!-- Rename argLine when using surefire -->
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