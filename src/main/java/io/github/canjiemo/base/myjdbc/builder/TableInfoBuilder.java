package io.github.canjiemo.base.myjdbc.builder;

import io.github.canjiemo.base.myjdbc.annotation.AuditFill;
import io.github.canjiemo.base.myjdbc.annotation.MyField;
import io.github.canjiemo.base.myjdbc.annotation.MyTable;
import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.error.MyJdbcErrorCode;
import io.github.canjiemo.base.myjdbc.metadata.TableInfo;
import io.github.canjiemo.base.myjdbc.utils.MyReflectionUtils;
import io.github.canjiemo.mycommon.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TableInfoBuilder implements BeanPostProcessor, Ordered {

    private static final Map<Class<?>, TableInfo> tableInfoMap = new ConcurrentHashMap<>();
    private static final Set<String> missingTableInfoLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Autowired(required = false)
    private BeanFactory beanFactory;

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
        if (scanPackages.isEmpty()) {
            log.warn("未能推导到任何实体扫描包，跳过 @MyTable 初始化");
            initTableCacheManager(scanPackages);
            return;
        }

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
            buildAuditFields(tableInfo);
            tableInfoMap.put(aClass, tableInfo);
        }

        // TableInfoBuilder初始化完成后立即初始化TableCacheManager
        // 确保缓存在任何SQL执行之前就已经准备好
        initTableCacheManager(scanPackages);
    }
    
    /**
     * 获取要扫描的包路径列表
     * 优先从 Spring Boot 官方 AutoConfigurationPackages 读取；
     * 若当前不是标准 Boot 上下文，则回退到 @SpringBootApplication 的扫描配置或主类包。
     * 均无法推导时返回空，避免兜底全局扫描带来启动性能和误扫描风险。
     */
    private List<String> getScanPackages() {
        Set<String> packages = new LinkedHashSet<>();

        addAutoConfigurationPackages(packages);
        if (!packages.isEmpty()) {
            log.info("从AutoConfigurationPackages获取包路径: {}", packages);
        }

        try {
            String mainClassName = findMainClass();
            if (mainClassName != null) {
                Class<?> mainClass = Class.forName(mainClassName);

                // 检查@SpringBootApplication注解
                SpringBootApplication springBootApp = mainClass.getAnnotation(SpringBootApplication.class);

                if (springBootApp != null) {
                    addConfiguredPackages(packages, springBootApp.scanBasePackages());
                    addConfiguredPackageClasses(packages, springBootApp.scanBasePackageClasses());
                    if (!packages.isEmpty()) {
                        log.info("从@SpringBootApplication扫描配置获取包路径: {}", packages);
                        return new ArrayList<>(packages);
                    }
                    Package mainPackage = mainClass.getPackage();
                    if (mainPackage != null && !mainPackage.getName().isBlank()) {
                        packages.add(mainPackage.getName());
                        log.info("从主类包路径获取: {}", mainPackage.getName());
                        return new ArrayList<>(packages);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("无法从@SpringBootApplication获取包路径: {}", e.getMessage());
        }

        if (!packages.isEmpty()) {
            return new ArrayList<>(packages);
        }
        log.warn("未找到 Spring Boot 自动配置包或主应用类，保持扫描包为空，避免兜底全局扫描");
        return List.of();
    }

    private void addAutoConfigurationPackages(Set<String> packages) {
        if (beanFactory == null || !AutoConfigurationPackages.has(beanFactory)) {
            return;
        }
        packages.addAll(AutoConfigurationPackages.get(beanFactory));
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
     * 扫描 tableInfo.fieldList，将标注了 @MyField(fill!=NONE) 的字段
     * 分别收集到 auditInsertFields / auditUpdateFields 并设置 setAccessible(true)。
     * auditUpdateFields 仅收录 fill=UPDATE_TIME 或 UPDATE_BY 的字段（CREATE_* 只在 INSERT 时填充）。
     * 此方法在启动时调用一次，运行时直接使用缓存列表，无需重复反射扫描。
     */
    private void buildAuditFields(TableInfo tableInfo) {
        List<Field> insertFields = new ArrayList<>();
        List<Field> updateFields = new ArrayList<>();
        for (Field field : tableInfo.getFieldList()) {
            MyField myField = field.getAnnotation(MyField.class);
            if (myField == null || myField.fill() == AuditFill.NONE) continue;
            try {
                field.setAccessible(true);
            } catch (InaccessibleObjectException e) {
                log.warn("审计字段 {}.{} 无法访问，跳过自动填充: {}",
                        tableInfo.getTableName(), field.getName(), e.getMessage());
                continue;
            }
            // 校验字段类型与 fill 策略是否兼容，不兼容则跳过（启动期快速失败，优于运行期静默错误）
            if (!isAuditFieldTypeCompatible(field, myField.fill())) {
                log.error("审计字段类型不兼容，已跳过自动填充: {}.{} (type={}, fill={}). "
                        + "时间字段支持 LocalDateTime/Date/Timestamp/Long，操作人字段支持 Long/Integer/String",
                        tableInfo.getTableName(), field.getName(),
                        field.getType().getSimpleName(), myField.fill());
                continue;
            }
            insertFields.add(field); // INSERT 填充所有审计字段
            if (myField.fill() == AuditFill.UPDATE_TIME || myField.fill() == AuditFill.UPDATE_BY) {
                updateFields.add(field); // UPDATE 只填充 UPDATE_* 字段
            }
        }
        if (!insertFields.isEmpty()) tableInfo.setAuditInsertFields(List.copyOf(insertFields));
        if (!updateFields.isEmpty()) tableInfo.setAuditUpdateFields(List.copyOf(updateFields));
    }

    /** 校验字段类型是否与 fill 策略兼容，不兼容时跳过缓存（启动期快速失败）。 */
    private static boolean isAuditFieldTypeCompatible(Field field, AuditFill fill) {
        Class<?> type = field.getType();
        return switch (fill) {
            case CREATE_TIME, UPDATE_TIME -> type == LocalDateTime.class
                    || type == java.util.Date.class
                    || type == java.sql.Timestamp.class
                    || type == Long.class || type == long.class;
            case CREATE_BY, UPDATE_BY -> type == Long.class || type == long.class
                    || type == Integer.class || type == int.class
                    || type == String.class;
            case NONE -> true;
        };
    }

    /**
     * 在TableInfoBuilder初始化完成后立即初始化TableCacheManager
     * 这样可以确保缓存优先于SQL执行进行初始化
     */
    private void initTableCacheManager(List<String> scanPackages) {
        try {
            if (scanPackages.isEmpty()) {
                TableCacheManager.clearCache();
                log.warn("TableCacheManager 未初始化任何扫描包，缓存保持为空");
                return;
            }
            TableCacheManager.initCache(scanPackages.toArray(new String[0]));

            log.info("TableCacheManager已在TableInfoBuilder后初始化完成，智能扫描包: {}", scanPackages);
            log.info("缓存统计: {}", TableCacheManager.getCacheStats());
        } catch (Exception e) {
            log.error("初始化TableCacheManager时发生异常", e);
        }
    }

    /**
     * 查找Spring Boot主类
     */
    private String findMainClass() {
        // 优先使用 JVM 启动命令推导主类，避免测试/容器环境下误识别 launcher main
        String command = System.getProperty("sun.java.command");
        if (command != null) {
            String[] parts = command.split("\\s+");
            if (parts.length > 0) {
                String candidate = parts[0];
                if (!candidate.endsWith(".jar")
                        && !candidate.contains("/")
                        && !candidate.contains("\\")
                        && candidate.contains(".")) {
                    return candidate;
                }
            }
        }

        // 退化到堆栈跟踪查找 main 方法
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if ("main".equals(element.getMethodName())) {
                return element.getClassName();
            }
        }

        return null;
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
