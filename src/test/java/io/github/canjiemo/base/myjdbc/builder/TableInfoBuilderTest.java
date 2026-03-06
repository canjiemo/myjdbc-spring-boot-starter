package io.github.canjiemo.base.myjdbc.builder;

import io.github.canjiemo.base.myjdbc.inheritedentity.InheritedPkUser;
import io.github.canjiemo.base.myjdbc.metadata.TableInfo;
import io.github.canjiemo.base.myjdbc.scanmarker.ScanMarker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

            assertTrue(packages.contains("io.github.canjiemo.base.myjdbc.scanmarker"));
        } finally {
            if (original == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, original);
            }
        }
    }

    @Test
    @DisplayName("继承父类主键字段的实体应能完成初始化")
    void initShouldSupportPkFieldDeclaredOnSuperclass() throws Exception {
        String key = "sun.java.command";
        String original = System.getProperty(key);
        System.setProperty(key, InheritedPkApplication.class.getName());

        try {
            TableInfoBuilder builder = new TableInfoBuilder();
            Method initMethod = TableInfoBuilder.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);

            assertDoesNotThrow(() -> initMethod.invoke(builder));

            TableInfo tableInfo = TableInfoBuilder.getTableInfo(InheritedPkUser.class);
            assertEquals("id", tableInfo.getPkFieldName());
            assertEquals("inherited_user", tableInfo.getTableName());
            assertEquals("io.github.canjiemo.base.myjdbc.inheritedentity.AbstractBaseEntity",
                    tableInfo.getPkField().getDeclaringClass().getName());
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

    @SpringBootApplication(scanBasePackageClasses = InheritedPkUser.class)
    static class InheritedPkApplication {
    }
}
