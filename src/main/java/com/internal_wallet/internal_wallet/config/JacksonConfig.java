package com.internal_wallet.internal_wallet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Provides a singleton ObjectMapper bean used for serialising / deserialising
     * idempotency response bodies in TransactionService and TransactionController.
     *
     * findAndRegisterModules() uses the ServiceLoader mechanism to discover and
     * register all Jackson modules on the classpath (including JavaTimeModule for
     * LocalDateTime support) without requiring an explicit import of the module class.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
