# Generate the self signed test certificate

$ keytool
-genkey \
-alias vertx \
-keypass localhost \
-keystore certificates.keystore \
-storepass localhost \
-keyalg RSA

# Convert to PCKS12

$ keytool \
-importkeystore \
-srckeystore certificates.keystore \
-destkeystore certificates.keystore \
-deststoretype pkcs12