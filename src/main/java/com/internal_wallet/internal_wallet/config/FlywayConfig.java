package com.internal_wallet.internal_wallet.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;

@Slf4j
@Configuration
public class FlywayConfig {

    /**
     * Creates the Flyway instance using the application's DataSource.
     * Spring Boot's Flyway auto-configuration is disabled (spring.flyway.enabled=false)
     * so this is the single authoritative Flyway bean.
     */
    @Bean
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("public")
                .baselineOnMigrate(true)   // handles non-empty Supabase DB gracefully
                .baselineVersion("0")      // baseline at 0 so V1 and V2 still run
                .load();
    }

    /**
     * Runs Flyway.migrate() and returns a marker bean.
     * The BeanFactoryPostProcessor below adds this bean as a dependency of
     * entityManagerFactory, so Hibernate never touches the schema before
     * the tables exist.
     */
    @Bean("flywayMigration")
    public Object runFlywayMigrations(Flyway flyway) {
        log.info("=== Flyway: running database migrations ===");
        var result = flyway.migrate();
        log.info("=== Flyway: {} migration(s) applied, schema version: {} ===",
                result.migrationsExecuted, result.targetSchemaVersion);
        return new Object();
    }

    /**
     * BeanFactoryPostProcessor that injects "flywayMigration" as a required
     * dependency of Spring Boot's "entityManagerFactory" bean.
     *
     * This replicates what FlywayAutoConfiguration.FlywayMigrationInitializer-
     * EntityManagerFactoryDependsOnPostProcessor does internally, but works even
     * when Spring Boot's Flyway auto-configuration is disabled.
     *
     * Must be static — BeanFactoryPostProcessors must be instantiated before
     * the containing @Configuration class itself is fully initialized.
     */
    @Bean
    public static BeanFactoryPostProcessor flywayEntityManagerDependencyPostProcessor() {
        return beanFactory -> {
            if (!(beanFactory instanceof ConfigurableListableBeanFactory clbf)) return;

            String emfBean = "entityManagerFactory";
            if (!clbf.containsBeanDefinition(emfBean)) {
                log.warn("Flyway ordering: bean '{}' not found — skipping dependency wiring", emfBean);
                return;
            }

            BeanDefinition bd = clbf.getBeanDefinition(emfBean);
            String[] existing = bd.getDependsOn();
            String[] updated = existing != null
                    ? Arrays.copyOf(existing, existing.length + 1)
                    : new String[1];
            updated[updated.length - 1] = "flywayMigration";
            bd.setDependsOn(updated);

            log.info("Flyway ordering: 'entityManagerFactory' now depends on 'flywayMigration'");
        };
    }
}
