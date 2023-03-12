mvn package -DskipTests -Dquarkus.package.type=uber-jar
rm -f ../../nop-app-mall/nop-cli.jar
cp ./target/nop-cli-2.0.0-SNAPSHOT-runner.jar ../../nop-app-mall/nop-cli.jar