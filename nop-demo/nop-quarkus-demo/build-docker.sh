mvn install -DskipTests
docker build . -f=src/main/docker/Dockerfile.jvm -t=nop/quarkus-demo 