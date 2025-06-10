package io.nop.spring.proxy;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.impl.FileResource;
import io.nop.http.api.utils.HttpCookieHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SpringWebProxy {
    static final Logger LOG = LoggerFactory.getLogger(SpringWebProxy.class);

    private File configDir;
    private Map<String, WebClientHolder> webClients = new ConcurrentHashMap<>();

    @Value("${nop.rest-proxy.config-dir:/nop/web-proxy/config}")
    public void setConfigDir(File configDir) {
        this.configDir = configDir;
    }

    @PostMapping(value = "/sse/{provider}/{*path}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sseStream(@PathVariable("provider") String provider, @PathVariable("path") String path,
                                  @RequestBody String body) {
        try {
            WebClientHolder webClient = getWebClient(provider);
            return callWeb(webClient, path, body)
                    .retryWhen(Retry.max(2) // 设置最大重试次数
                            .filter(throwable -> throwable instanceof UnauthorizedException)
                            .doBeforeRetryAsync(retrySignal -> {
                                LOG.info("Unauthorized, refreshing token and retrying...");
                                return refreshAccessToken(webClient).then(Mono.empty());
                            }));
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    @PostMapping(value = "/rest/{provider}/{*path}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<String> restProxy(@PathVariable("provider") String provider, @PathVariable("path") String path,
                                             @RequestBody String body) {
        try {
            WebClientHolder webClient = getWebClient(provider);
            return callRest(webClient, path, body)
                    .retryWhen(Retry.max(2) // 设置最大重试次数
                            .filter(throwable -> throwable instanceof UnauthorizedException)
                            .doBeforeRetryAsync(retrySignal -> {
                                LOG.info("Unauthorized, refreshing token and retrying...");
                                return refreshAccessToken(webClient).then(Mono.empty());
                            })); // 将Mono转换为CompletableFuture（实现CompletionStage）
        } catch (Exception e) {
            return Mono.error(e); // 异常时返回失败的Future
        }
    }

    // 新增：处理普通REST请求的方法（返回Mono）
    private Mono<String> callRest(WebClientHolder webClient, String path, String body) {
        return webClient.getWebClient().post()
                .uri(path)
                .headers(headers -> {
                    // 传递预配置的Authorization和Cookie头
                    if (webClient.getAuthorization() != null) {
                        headers.set("Authorization", webClient.getAuthorization());
                    }
                    if (webClient.getCookie() != null) {
                        headers.set("Cookie", webClient.getCookie());
                    }
                })
                .bodyValue(body) // 设置请求体
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    // 仅处理401未授权错误（与sseStream逻辑一致）
                    if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
                        return Mono.error(new UnauthorizedException("Unauthorized"));
                    }
                    // 其他4xx错误直接传递异常
                    return response.createException();
                })
                .bodyToMono(String.class) // 单响应体转换为Mono
                .doOnSuccess(text -> LOG.info("nop.rest.recv: {}", text)) // 成功时记录响应
                .doOnError(e -> LOG.error("restProxy请求失败: ", e)); // 错误时记录日志
    }


    Flux<String> callWeb(WebClientHolder webClient, String path, String body) {
        return webClient.getWebClient().post()
                .uri(path).headers(headers -> {
                    if (webClient.getAuthorization() != null) {
                        headers.set("authorization", webClient.getAuthorization());
                    }
                    if (webClient.getCookie() != null) {
                        headers.set("cookie", webClient.getCookie());
                    }
                })
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.value() == 401, response -> {
                    return Mono.error(new UnauthorizedException("Unauthorized"));
                })
                .bodyToFlux(String.class)
                .doOnEach(signal -> {
                    String text = signal.get();
                    LOG.info("nop.recv:{}", text);
                })
                .doOnError(e -> LOG.error("Error occurred: ", e))
                .doOnComplete(() -> LOG.info("Chat session completed"));
    }

    // 封装的刷新 accessToken 的方法
    Mono<String> refreshAccessToken(WebClientHolder webClient) {
        WebClientConfig config = webClient.getConfig();
        if (config.getRefreshAuthUrl() == null)
            return Mono.empty();

        return webClient.getWebClient().post()
                .uri(config.getRefreshAuthUrl())
                .headers(headers -> {
                    if (webClient.getAuthorization() != null) {
                        headers.set("authorization", webClient.getAuthorization());
                    }
                    if (webClient.getCookie() != null) {
                        headers.set("cookie", webClient.getCookie());
                    }
                })
                .retrieve()
                .toEntity(String.class) // 假设能直接获取 token，具体根据实际返回类型调整
                .doOnNext(response -> {
                    String cookie = response.getHeaders().getFirst("set-cookie");
                    cookie = HttpCookieHelper.updateCookie(webClient.getCookie(), cookie);
                    webClient.setCookie(cookie);
                    String data = response.getBody();
                    Map<String, Object> map = JsonTool.parseMap(data);
                    String tokenPath = config.getTokenPath();
                    String token = (String) BeanTool.getComplexProperty(map, tokenPath);
                    webClient.setAuthorization("Bearer " + token);
                }).map(HttpEntity::getBody)
                .doOnError(e -> LOG.error("Error occurred while refreshing token: ", e));
    }

    WebClientHolder getWebClient(String provider) {
        return webClients.computeIfAbsent(provider, this::newWebClient);
    }

    WebClientHolder newWebClient(String provider) {
        WebClientConfig config = loadConfig(provider);

        WebClient client = WebClient.builder().baseUrl(config.getBaseUrl()).defaultHeaders(headers -> {
            if (config.getHeaders() != null)
                headers.setAll(config.getHeaders());
        }).build();

        WebClientHolder holder = new WebClientHolder();
        holder.setConfig(config);
        holder.setWebClient(client);
        holder.setCookie(config.getHeader("cookie"));
        holder.setAuthorization(config.getHeader("authorization"));
        return holder;
    }

    WebClientConfig loadConfig(String provider) {
        File configFile = new File(configDir, provider + ".cfg.yaml");
        WebClientConfig config = JsonTool.parseBeanFromResource(new FileResource(configFile), WebClientConfig.class);
        return config;
    }
}