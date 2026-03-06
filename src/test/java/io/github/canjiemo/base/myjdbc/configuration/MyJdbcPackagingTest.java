package io.github.canjiemo.base.myjdbc.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MyJDBC 打包约束测试")
class MyJdbcPackagingTest {

    @Test
    @DisplayName("starter 不应向集成应用注入 application.properties")
    void starterShouldNotShipApplicationProperties() throws Exception {
        List<URL> resources = Collections.list(
                Thread.currentThread().getContextClassLoader().getResources("application.properties"));

        boolean projectResourcePresent = resources.stream()
                .map(URL::toString)
                .anyMatch(url -> url.contains("/target/classes/application.properties"));

        assertFalse(projectResourcePresent,
                () -> "starter 不应打包 application.properties，当前资源为: " + resources);
    }

    @Test
    @DisplayName("Boot 3 AutoConfiguration.imports 应导出全部自动配置类")
    void autoConfigurationImportsShouldExposeAllAutoConfigurations() {
        List<String> candidates = ImportCandidates.load(
                AutoConfiguration.class,
                Thread.currentThread().getContextClassLoader()).getCandidates();

        assertTrue(candidates.contains(MyJdbcTemplateAutoConfiguration.class.getName()));
        assertTrue(candidates.contains(MyJdbcAutoConfiguration.class.getName()));
        assertTrue(candidates.contains(MyJdbcMetricsAutoConfiguration.class.getName()));
    }

    @Test
    @DisplayName("spring.factories 应保留自动配置与 FailureAnalyzer 兼容声明")
    void springFactoriesShouldRetainCompatibilityEntries() throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/spring.factories")) {
            assertNotNull(stream, "应存在 META-INF/spring.factories");
            properties.load(stream);
        }

        String autoConfigurations = properties.getProperty("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
        assertNotNull(autoConfigurations);
        assertTrue(autoConfigurations.contains(MyJdbcTemplateAutoConfiguration.class.getName()));
        assertTrue(autoConfigurations.contains(MyJdbcAutoConfiguration.class.getName()));
        assertTrue(autoConfigurations.contains(MyJdbcMetricsAutoConfiguration.class.getName()));
        assertEquals(MyJdbcInitFailureAnalyzer.class.getName(),
                properties.getProperty("org.springframework.boot.diagnostics.FailureAnalyzer"));
    }
}
