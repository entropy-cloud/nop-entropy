
# HTTP Client

IHttpClient provides an HTTP client abstraction that can execute HTTP calls asynchronously and supports the HTTP/2 protocol.

* The nop-http-api package defines the API interfaces
* nop-http-client-jdk provides a default implementation based on the JDK's built-in client
* nop-http-client-apache provides an implementation using Apache HttpClient 5.
* nop-http-client-oauth provides configuration-driven automatic setting of access tokens

Include the corresponding package for whichever HTTP client implementation you need.


## Retrieve Client IP

The backend exposes a unified IHttpServerContext interface that encapsulates the server-side context. The IClientIpFetcher interface obtains the client IP from IHttpServerContext.
Because requests may pass through intermediate proxy servers, the real client IP needs to be read from the X-Forwarded-For or Forwarded HTTP headers. You can customize nopClientIpFetcher to change the default retrieval logic.

For requests that enter through services like SpringGraphQLWebService, ApiRequest sets the nop-client-addr header. IServiceContext provides the getRequestClientIp() helper function.

<!-- SOURCE_MD5:576b02d42c0501106ced1b16316b83c4-->
