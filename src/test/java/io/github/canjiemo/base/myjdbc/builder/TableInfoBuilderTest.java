package io.github.canjiemo.base.myjdbc.builder;

import io.github.canjiemo.base.myjdbc.inheritedentity.InheritedPkUser;
import io.github.canjiemo.base.myjdbc.metadata.TableInfo;
import io.github.canjiemo.base.myjdbc.scanmarker.ScanMarker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TableInfoBuilder 包扫描推导测试")
class TableInfoBuilderTest {

    @Test
    @DisplayName("AutoConfigurationPackages 与 SpringBootApplication 扫描配置应合并")
    void getScanPackagesShouldMergeAutoConfigurationPackagesAndApplicationScanPackages() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        AutoConfigurationPackages.register(beanFactory,
                "com.example.demo",
                "io.github.canjiemo.base.myjdbc.scanmarker");

        TableInfoBuilder builder = new TableInfoBuilder();
        setBeanFactory(builder, beanFactory);
        String key = "sun.java.command";
        String original = System.getProperty(key);
        System.setProperty(key, ScanBasePackageClassesApplication.class.getName());

        try {
            List<String> packages = invokeGetScanPackages(builder);
            assertTrue(packages.contains("com.example.demo"));
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
    @DisplayName("scanBasePackages 应补充应用显式声明的父包路径")
    void getScanPackagesShouldIncludeExplicitScanBasePackages() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        AutoConfigurationPackages.register(beanFactory, "com.seer.fitness.edu");

        TableInfoBuilder builder = new TableInfoBuilder();
        setBeanFactory(builder, beanFactory);
        String key = "sun.java.command";
        String original = System.getProperty(key);
        System.setProperty(key, ScanBasePackagesApplication.class.getName());

        try {
            List<String> packages = invokeGetScanPackages(builder);
            assertTrue(packages.contains("com.seer.fitness.edu"));
            assertTrue(packages.contains("com.seer.fitness"));
        } finally {
            if (original == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, original);
            }
        }
    }

    @Test
    @DisplayName("scanBasePackageClasses 配置应参与扫描路径推导")
    void getScanPackagesShouldUseScanBasePackageClasses() throws Exception {
        String key = "sun.java.command";
        String original = System.getProperty(key);
        System.setProperty(key, ScanBasePackageClassesApplication.class.getName());

        try {
            TableInfoBuilder builder = new TableInfoBuilder();
            List<String> packages = invokeGetScanPackages(builder);

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
    @DisplayName("缺少 Boot 上下文时不应兜底扫描全局业务包")
    void getScanPackagesShouldStayEmptyWithoutBootHints() throws Exception {
        String key = "sun.java.command";
        String original = System.getProperty(key);
        System.setProperty(key, "org.apache.maven.surefire.booter.ForkedBooter");

        try {
            TableInfoBuilder builder = new TableInfoBuilder();
            assertTrue(invokeGetScanPackages(builder).isEmpty());
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

    @SpringBootApplication(scanBasePackages = "com.seer.fitness")
    static class ScanBasePackagesApplication {
    }

    @SpringBootApplication(scanBasePackageClasses = InheritedPkUser.class)
    static class InheritedPkApplication {
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeGetScanPackages(TableInfoBuilder builder) throws Exception {
        Method method = TableInfoBuilder.class.getDeclaredMethod("getScanPackages");
        method.setAccessible(true);
        return (List<String>) method.invoke(builder);
    }

    private static void setBeanFactory(TableInfoBuilder builder, DefaultListableBeanFactory beanFactory) throws Exception {
        Field field = TableInfoBuilder.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(builder, beanFactory);
    }
}
