package io.github.canjiemo.base.myjdbc.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
