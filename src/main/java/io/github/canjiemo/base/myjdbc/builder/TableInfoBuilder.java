package io.github.canjiemo.base.myjdbc.builder;

import io.github.canjiemo.base.myjdbc.annotation.MyTable;
import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.error.MyJdbcErrorCode;
import io.github.canjiemo.base.myjdbc.metadata.TableInfo;
import io.github.canjiemo.base.myjdbc.utils.MyReflectionUtils;
import io.github.canjiemo.mycommon.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TableInfoBuilder implements BeanPostProcessor, Ordered {

    private static final Map<Class<?>, TableInfo> tableInfoMap = new ConcurrentHashMap<>();
    private static final Set<String> missingTableInfoLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @PostConstruct
    private void init() {
        log.info("初始化@MyTable信息...");
        tableInfoMap.clear();
        missingTableInfoLogged.clear();
        String osName = System.getProperty("os.name");
        log.info("系统信息:{}", osName);

        // 获取要扫描的包路径列表
        List<String> scanPackages = getScanPackages();
        log.info("准备扫描的包路径: {}", scanPackages);

        // 使用Spring的类路径扫描器
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(MyTable.class));

        Set<Class<?>> classSet = new HashSet<>();

        // 扫描所有指定的包
        for (String basePackage : scanPackages) {
            try {
                Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
                for (BeanDefinition bd : candidates) {
                    Class<?> clazz = Class.forName(bd.getBeanClassName());
                    classSet.add(clazz);
                }
            } catch (Exception e) {
                log.warn("扫描包 {} 时发生异常: {}", basePackage, e.getMessage());
            }
        }

        log.info("共找到@MyTable注解的类{}个", classSet.size());

        // 处理每个找到的类
        for (Class<?> aClass : classSet) {
            log.info("{}", aClass.toString());
            MyTable annotation = aClass.getAnnotation(MyTable.class);
            Field pkField = MyReflectionUtils.findFieldInHierarchy(aClass, annotation.pkField());
            if (pkField == null) {
                log.error("实体主键字段配置错误: class={}, pkField={}", aClass.getName(), annotation.pkField());
                throw new BusinessException(MyJdbcErrorCode.CONFIG_ERROR.userMessage());
            }
            TableInfo tableInfo = new TableInfo()
                    .setTableName(annotation.value())
                    .setClazz(aClass)
                    .setPkField(pkField)
                    .setPkFieldName(annotation.pkField())
                    .setPkColumnName(annotation.pkColumn())
                    .setFieldList(MyReflectionUtils.getFieldList(aClass))
                    .setDelColumnName(annotation.delColumn())
                    .setDelFieldName(annotation.delField())
                    .setDelValue(annotation.delValue());
            tableInfoMap.put(aClass, tableInfo);
        }

        // TableInfoBuilder初始化完成后立即初始化TableCacheManager
        // 确保缓存在任何SQL执行之前就已经准备好
        initTableCacheManager();
    }
    
    /**
     * 获取要扫描的包路径列表
     * 优先从@SpringBootApplication的scanBasePackages读取（支持多个包）
     * 如果没有配置则使用主类所在包
     * 最后fallback到常见的业务包
     */
    private List<String> getScanPackages() {
        Set<String> packages = new LinkedHashSet<>();

        try {
            String mainClassName = findMainClass();
            if (mainClassName != null) {
                Class<?> mainClass = Class.forName(mainClassName);

                // 检查@SpringBootApplication注解
                org.springframework.boot.autoconfigure.SpringBootApplication springBootApp =
                    mainClass.getAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);

                if (springBootApp != null) {
                    addConfiguredPackages(packages, springBootApp.scanBasePackages());
                    addConfiguredPackageClasses(packages, springBootApp.scanBasePackageClasses());
                    if (!packages.isEmpty()) {
                        log.info("从@SpringBootApplication扫描配置获取包路径: {}", packages);
                        return new ArrayList<>(packages);
                    }
                }

                // 如果没有配置scanBasePackages，使用主类所在的包
                int lastDotIndex = mainClassName.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    String packageName = mainClassName.substring(0, lastDotIndex);
                    packages.add(packageName);
                    log.info("从主类包路径获取: {}", packageName);
                    return new ArrayList<>(packages);
                }
            }
        } catch (Exception e) {
            log.warn("无法从@SpringBootApplication获取包路径: {}", e.getMessage());
        }

        // fallback: 使用常见的业务包前缀
        packages.addAll(Arrays.asList("com", "cn", "org", "io"));
        log.info("使用fallback包路径: {}", packages);
        return new ArrayList<>(packages);
    }

    private void addConfiguredPackages(Set<String> packages, String[] scanBasePackages) {
        if (scanBasePackages == null) {
            return;
        }
        for (String basePackage : scanBasePackages) {
            if (basePackage != null && !basePackage.isBlank()) {
                packages.add(basePackage.trim());
            }
        }
    }

    private void addConfiguredPackageClasses(Set<String> packages, Class<?>[] scanBasePackageClasses) {
        if (scanBasePackageClasses == null) {
            return;
        }
        for (Class<?> packageClass : scanBasePackageClasses) {
            if (packageClass == null || packageClass.getPackage() == null) {
                continue;
            }
            String packageName = packageClass.getPackage().getName();
            if (!packageName.isBlank()) {
                packages.add(packageName);
            }
        }
    }

    /**
     * 在TableInfoBuilder初始化完成后立即初始化TableCacheManager
     * 这样可以确保缓存优先于SQL执行进行初始化
     */
    private void initTableCacheManager() {
        try {
            List<String> scanPackages = getScanPackages();
            TableCacheManager.initCache(scanPackages.toArray(new String[0]));

            log.info("TableCacheManager已在TableInfoBuilder后初始化完成，智能扫描包: {}", scanPackages);
            log.info("缓存统计: {}", TableCacheManager.getCacheStats());
        } catch (Exception e) {
            log.warn("初始化TableCacheManager时发生异常，使用fallback扫描: {}", e.getMessage());
            // fallback: 如果无法推导包路径，使用常见的业务包前缀
            fallbackScan();
        }
    }

    /**
     * 查找Spring Boot主类
     */
    private String findMainClass() {
        // 方法1：优先使用 JVM 启动命令推导主类，避免测试/容器环境下误识别 launcher main
        String mainClass = System.getProperty("sun.java.command");
        if (mainClass != null && mainClass.contains(".")) {
            String[] parts = mainClass.split("\\s+");
            if (parts.length > 0 && parts[0].contains(".")) {
                try {
                    Class<?> candidate = Class.forName(parts[0]);
                    if (candidate.getAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class) != null) {
                        return parts[0];
                    }
                } catch (ClassNotFoundException ignored) {
                    // ignore and fallback to stack trace detection
                }
            }
        }

        // 方法2：通过堆栈跟踪查找main方法
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if ("main".equals(element.getMethodName())) {
                return element.getClassName();
            }
        }

        return null;
    }
    
    /**
     * fallback扫描策略
     */
    private void fallbackScan() {
        try {
            TableCacheManager.initCache("com.example", "com", "cn", "org");
            log.info("使用fallback包路径扫描@MyTable注解");
        } catch (Exception e) {
            log.error("Fallback扫描也失败了: {}", e.getMessage());
        }
    }

    public static TableInfo getTableInfo(Class<?> aClass){
        TableInfo tableInfo = tableInfoMap.get(aClass);
        if (tableInfo == null) {
            String className = aClass == null ? "null" : aClass.getName();
            if (missingTableInfoLogged.add(className)) {
                log.error("缺少@MyTable注解或未完成初始化: class={}", className);
            } else {
                log.debug("重复缺少@MyTable配置: class={}", className);
            }
            throw new BusinessException(MyJdbcErrorCode.CONFIG_ERROR.userMessage());
        }
        return tableInfo;
    }


    @Override
    public int getOrder() {
        return 0;
    }
}
