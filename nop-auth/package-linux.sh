./mvnw clean package -DskipTests \
-Dquarkus.native.container-build=true \
-Pnative \
-Dquarkus.native.native-image-xmx=4g \
-e
