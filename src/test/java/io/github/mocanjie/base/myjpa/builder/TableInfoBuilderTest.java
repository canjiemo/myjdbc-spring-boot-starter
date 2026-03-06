package io.github.mocanjie.base.myjpa.builder;

import io.github.mocanjie.base.myjpa.scanmarker.ScanMarker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TableInfoBuilder 包扫描推导测试")
class TableInfoBuilderTest {

    @Test
    @DisplayName("scanBasePackageClasses 配置应参与扫描路径推导")
    void getScanPackagesShouldUseScanBasePackageClasses() throws Exception {
        String key = "sun.java.command";
        String original = System.getProperty(key);
        System.setProperty(key, ScanBasePackageClassesApplication.class.getName());

        try {
            TableInfoBuilder builder = new TableInfoBuilder();
            Method method = TableInfoBuilder.class.getDeclaredMethod("getScanPackages");
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<String> packages = (List<String>) method.invoke(builder);

            assertTrue(packages.contains("io.github.mocanjie.base.myjpa.scanmarker"));
        } finally {
            if (original == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, original);
            }
        }
    }

    @SpringBootApplication(scanBasePackageClasses = ScanMarker.class)
    static class ScanBasePackageClassesApplication {
    }
}
