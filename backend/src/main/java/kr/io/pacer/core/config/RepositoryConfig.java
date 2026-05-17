package kr.io.pacer.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "kr.io.pacer.core.repository.jpa")
@EnableRedisRepositories(basePackages = "kr.io.pacer.core.repository.redis")
public class RepositoryConfig {
}
