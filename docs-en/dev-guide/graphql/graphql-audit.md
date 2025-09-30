# Service Invocation Auditing

You can track entity modification history at the ORM layer via IOrmInterceptor.

If you want to record similar invocation logs at the GraphQL layer, you can follow the implementation of DefaultGraphQLAuditer, register a bean with the id nopGraphQLAuditer, and implement the IGraphQLAuditer interface.
<!-- SOURCE_MD5:eb8eef176422da343a349a1b1696f0ad-->
