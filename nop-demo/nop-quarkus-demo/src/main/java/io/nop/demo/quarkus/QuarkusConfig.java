package io.nop.demo.quarkus;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class QuarkusConfig {
    @Inject
    RedisDataSource redisDataSource;
}
