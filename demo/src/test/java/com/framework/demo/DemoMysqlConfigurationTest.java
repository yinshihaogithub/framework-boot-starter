package com.framework.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DemoMysqlConfigurationTest {

    @Test
    void demoDefaultsToMysqlAndRunsFrameworkInitScript() {
        Properties properties = applicationProperties();

        assertThat(properties.getProperty("spring.datasource.url")).startsWith("jdbc:mysql://");
        assertThat(properties.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("always");
        assertThat(properties.getProperty("spring.sql.init.continue-on-error")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.schema-locations[0]"))
                .isEqualTo("optional:file:./sql/mysql/framework_boot_starter_init.sql");
        assertThat(properties.getProperty("spring.sql.init.schema-locations[1]"))
                .isEqualTo("optional:file:../sql/mysql/framework_boot_starter_init.sql");
    }

    private static Properties applicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
