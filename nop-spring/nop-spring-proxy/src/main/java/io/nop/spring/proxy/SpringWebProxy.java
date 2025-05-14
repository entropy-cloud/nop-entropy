package io.nop.spring.proxy;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class SpringWebProxy {
    static final Logger LOG = LoggerFactory.getLogger(SpringWebProxy.class);

    private File configDir;
    private Map<String, WebClient> webClients = new ConcurrentHashMap<>();

    @Value("${nop.rest-proxy.config-dir:/nop/web-proxy/config}")
    public void setConfigDir(File configDir) {
        this.configDir = configDir;
    }

    @PostExchange(value = "/sse/{provider}/{path:.*}")
    public Flux<String> sseStream(@PathVariable("provider") String provider, @PathVariable("path") String path,
                                  @RequestBody String body) {
        WebClient webClient = getWebClient(provider);
        return webClient.post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(e -> LOG.error("Error occurred: ", e))
                .doOnComplete(() -> LOG.info("Chat session completed"));
    }

    WebClient getWebClient(String provider) {
        return webClients.computeIfAbsent(provider, this::newWebClient);
    }

    WebClient newWebClient(String provider) {
        File configFile = new File(configDir, provider + ".cfg.yaml");
        WebClientConfig config = JsonTool.parseBeanFromResource(new FileResource(configFile), WebClientConfig.class);
        return WebClient.builder().baseUrl(config.getBaseUrl()).defaultHeaders(headers -> {
            if (config.getHeaders() != null)
                headers.setAll(config.getHeaders());
        }).build();
    }
}
