# Common Errors

## No Session

![](images/no-session-orm.png)

Classes annotated with `@SingleSession` and `@Transactional` need to execute the CodeGenTask code-generation task during Maven packaging. Therefore, run mvn compile -DskipTests first, and only then can you start debugging in IDEA.
<!-- SOURCE_MD5:9e65e328f1ed07589af5ed342d147119-->
