package io.nop.spring.web.filter;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.2-002 (plan 2406): direct branch tests for ZipContentEncodingFilter. The test subclass
 * overrides the package-private {@code getResource} so branch decisions do not depend on real
 * {@code META-INF/resources} content (the module ships none), and {@link BeanContainer} is
 * registered/deregistered around each case (the "/" and "/index.html" branch calls
 * {@code BeanContainer.instance()} which throws when uninitialized).
 */
public class TestZipContentEncodingFilter {

    private Object savedProvider;

    @BeforeEach
    public void registerBeanContainer() throws Exception {
        savedProvider = providerField().get(null);
        BeanContainer.registerInstance(stubContainer());
    }

    @AfterEach
    public void restoreBeanContainer() throws Exception {
        providerField().set(null, savedProvider);
    }

    private static Field providerField() throws NoSuchFieldException {
        Field f = BeanContainer.class.getDeclaredField("_provider");
        f.setAccessible(true);
        return f;
    }

    private static IBeanContainer stubContainer() {
        return (IBeanContainer) Proxy.newProxyInstance(TestZipContentEncodingFilter.class.getClassLoader(),
                new Class[]{IBeanContainer.class}, (proxy, method, args) -> {
                    if (method.getReturnType() == boolean.class)
                        return false;
                    return null;
                });
    }

    /** getResource is package-private and returns ClassPathResource, so override it to point at a
     * resource that certainly exists on the test classpath (this test class) when the requested
     * path is "present". */
    static class ProbeFilter extends ZipContentEncodingFilter {
        final Set<String> present = new HashSet<>();
        final List<String> zipHeaderPaths = new ArrayList<>();

        ProbeFilter(String... presentPaths) {
            present.addAll(java.util.Arrays.asList(presentPaths));
        }

        @Override
        ClassPathResource getResource(String path) {
            if (present.contains(path)) {
                return new ClassPathResource(
                        TestZipContentEncodingFilter.class.getName().replace('.', '/') + ".class",
                        getClass().getClassLoader());
            }
            return new ClassPathResource("no-such-resource-" + path.replace('/', '_'), getClass().getClassLoader());
        }

        @Override
        void setZipHeader(String path, HttpServletResponse response) {
            zipHeaderPaths.add(path);
            super.setZipHeader(path, response);
        }
    }

    static class Recorder {
        String requestURI = "/";
        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, String> contentType = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String forwarded;
        boolean chainCalled;
        final StringWriter body = new StringWriter();
        final List<String> setContentTypes = new ArrayList<>();

        HttpServletRequest newRequest() {
            return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpServletRequest.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getRequestURI":
                                return requestURI;
                            case "getRequestDispatcher":
                                RequestDispatcher dispatcher = (RequestDispatcher) Proxy.newProxyInstance(
                                        getClass().getClassLoader(), new Class[]{RequestDispatcher.class},
                                        (dProxy, dMethod, dArgs) -> {
                                            if (dMethod.getName().equals("forward")) {
                                                forwarded = (String) args[0];
                                            }
                                            return null;
                                        });
                                return dispatcher;
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        HttpServletResponse newResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpServletResponse.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setHeader":
                            case "addHeader":
                                headers.put((String) args[0], (String) args[1]);
                                return null;
                            case "setContentType":
                                setContentTypes.add((String) args[0]);
                                headers.put("content-type", (String) args[0]);
                                return null;
                            case "getContentType":
                                return headers.get("content-type");
                            case "getWriter":
                                return new PrintWriter(body);
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        FilterChain newChain() {
            return (FilterChain) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{FilterChain.class}, (proxy, method, args) -> {
                        if (method.getName().equals("doFilter")) {
                            chainCalled = true;
                        }
                        return null;
                    });
        }

        private static Object defaultFor(Class<?> type) {
            if (type == boolean.class)
                return false;
            if (type == int.class)
                return 0;
            if (type == long.class)
                return 0L;
            return null;
        }
    }

    // ======================= branch decisions =======================

    @Test
    public void testJsWithGzForwardsToGzAndSetsZipHeader() throws Exception {
        ProbeFilter filter = new ProbeFilter("META-INF/resources/app.js.gz");
        Recorder rec = new Recorder();
        rec.requestURI = "/app.js";

        filter.doFilterInternal(rec.newRequest(), rec.newResponse(), rec.newChain());

        assertEquals("/app.js.gz", rec.forwarded, "gz variant must be forwarded");
        assertEquals("gzip", rec.headers.get("content-encoding"));
        assertEquals("text/javascript", rec.headers.get("content-type"), "content type mapped from extension");
        assertEquals(List.of("/app.js"), filter.zipHeaderPaths);
        assertFalse(rec.chainCalled, "gz hit must not fall through to the chain");
    }

    @Test
    public void testJsWithoutGzFallsThroughToChain() throws Exception {
        ProbeFilter filter = new ProbeFilter(); // no gz
        Recorder rec = new Recorder();
        rec.requestURI = "/app.js";

        filter.doFilterInternal(rec.newRequest(), rec.newResponse(), rec.newChain());

        assertTrue(rec.chainCalled, "no gz variant -> chain");
        assertEquals(null, rec.forwarded);
        assertTrue(filter.zipHeaderPaths.isEmpty());
    }

    @Test
    public void testGzRequestSetsZipHeaderAndContinuesChain() throws Exception {
        ProbeFilter filter = new ProbeFilter();
        Recorder rec = new Recorder();
        rec.requestURI = "/assets/app.js.gz";

        filter.doFilterInternal(rec.newRequest(), rec.newResponse(), rec.newChain());

        assertEquals(List.of("/assets/app.js.gz"), filter.zipHeaderPaths);
        assertEquals("gzip", rec.headers.get("content-encoding"));
        assertTrue(rec.chainCalled, ".gz requests continue down the chain with the wrapper");
    }

    @Test
    public void testIndexHtmlWithoutResourcesFallsThrough() throws Exception {
        ProbeFilter filter = new ProbeFilter(); // no index.html.gz / index.html
        Recorder rec = new Recorder();
        rec.requestURI = "/index.html";

        filter.doFilterInternal(rec.newRequest(), rec.newResponse(), rec.newChain());

        assertTrue(rec.chainCalled, "index.html with no bundled variant -> chain");
        assertTrue(filter.zipHeaderPaths.isEmpty());
    }

    @Test
    public void testResponseWrapperIgnoresContentType() throws Exception {
        ProbeFilter filter = new ProbeFilter();
        Recorder rec = new Recorder();
        HttpServletResponse original = rec.newResponse();

        HttpServletResponse wrapper = filter.newResponseWrapper(original);
        wrapper.setContentType("text/plain");
        wrapper.setHeader("content-type", "text/plain");
        wrapper.addHeader("Content-Type", "text/plain");
        wrapper.setHeader("x-custom", "v");

        assertTrue(rec.setContentTypes.isEmpty(), "wrapper must swallow setContentType");
        assertEquals(null, rec.headers.get("content-type"), "wrapper must drop content-type headers");
        assertEquals("v", rec.headers.get("x-custom"), "other headers pass through");
    }

    /**
     * Rule #22 request-chain criterion: proxy request (with a bundled .gz) -> filter -> captured
     * response/forward output, in one test.
     */
    @Test
    public void testRequestChainEntryToOutput() throws Exception {
        ProbeFilter filter = new ProbeFilter("META-INF/resources/index.html.gz");
        Recorder rec = new Recorder();
        rec.requestURI = "/index.html";

        filter.doFilterInternal(rec.newRequest(), rec.newResponse(), rec.newChain());

        assertEquals("/index.html.gz", rec.forwarded);
        assertEquals(List.of("/index.html"), filter.zipHeaderPaths);
        assertEquals("gzip", rec.headers.get("content-encoding"));
        assertEquals("text/html", rec.headers.get("content-type"));
    }
}
