# Service Call Audit

You can track entity modification history at the ORM layer using IOrmInterceptor.

If you want to record similar call logs at the GraphQL layer, you can follow the implementation of DefaultGraphQLAuditer and register a bean with id "nopGraphQLAuditer" that implements the IGraphQlAuditer interface.