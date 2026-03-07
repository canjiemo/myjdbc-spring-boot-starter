package io.github.canjiemo.base.myjdbc.scope;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * myjdbc 执行作用域。
 *
 * <p>用于在一个短生命周期内临时关闭部分内建能力，或显式放开危险写操作。
 * 与框架自动 SQL 改写解耦，适合 raw SQL、lambdaQuery、lambdaUpdate 共用。
 */
public final class MyJdbcScope {

    private static final ThreadLocal<Map<MyJdbcFeature, Integer>> DISABLED_FEATURES = new ThreadLocal<>();
    private static final ThreadLocal<Integer> UNSAFE_WRITE_DEPTH = new ThreadLocal<>();

    private MyJdbcScope() {
    }

    public static void skip(MyJdbcFeature feature) {
        if (feature == null) {
            return;
        }
        Map<MyJdbcFeature, Integer> features = DISABLED_FEATURES.get();
        if (features == null) {
            features = new EnumMap<>(MyJdbcFeature.class);
            DISABLED_FEATURES.set(features);
        }
        features.merge(feature, 1, Integer::sum);
    }

    public static void restore(MyJdbcFeature feature) {
        if (feature == null) {
            return;
        }
        Map<MyJdbcFeature, Integer> features = DISABLED_FEATURES.get();
        if (features == null) {
            return;
        }
        Integer depth = features.get(feature);
        if (depth == null) {
            return;
        }
        if (depth <= 1) {
            features.remove(feature);
        } else {
            features.put(feature, depth - 1);
        }
        if (features.isEmpty()) {
            DISABLED_FEATURES.remove();
        }
    }

    public static boolean isSkipped(MyJdbcFeature feature) {
        Map<MyJdbcFeature, Integer> features = DISABLED_FEATURES.get();
        return features != null && features.getOrDefault(feature, 0) > 0;
    }

    public static boolean isTenantSkipped() {
        return isSkipped(MyJdbcFeature.TENANT);
    }

    public static boolean isLogicDeleteSkipped() {
        return isSkipped(MyJdbcFeature.LOGIC_DELETE);
    }

    public static <T> T allTenants(Supplier<T> supplier) {
        return without(MyJdbcFeature.TENANT, supplier);
    }

    public static void allTenants(Runnable runnable) {
        without(MyJdbcFeature.TENANT, runnable);
    }

    public static <T> T withDeleted(Supplier<T> supplier) {
        return without(MyJdbcFeature.LOGIC_DELETE, supplier);
    }

    public static void withDeleted(Runnable runnable) {
        without(MyJdbcFeature.LOGIC_DELETE, runnable);
    }

    public static <T> T without(MyJdbcFeature feature, Supplier<T> supplier) {
        skip(feature);
        try {
            return supplier.get();
        } finally {
            restore(feature);
        }
    }

    public static void without(MyJdbcFeature feature, Runnable runnable) {
        skip(feature);
        try {
            runnable.run();
        } finally {
            restore(feature);
        }
    }

    public static boolean isUnsafeWriteAllowed() {
        Integer depth = UNSAFE_WRITE_DEPTH.get();
        return depth != null && depth > 0;
    }

    public static <T> T allowUnsafeWrite(Supplier<T> supplier) {
        enterUnsafeWrite();
        try {
            return supplier.get();
        } finally {
            exitUnsafeWrite();
        }
    }

    public static void allowUnsafeWrite(Runnable runnable) {
        enterUnsafeWrite();
        try {
            runnable.run();
        } finally {
            exitUnsafeWrite();
        }
    }

    public static void clear() {
        DISABLED_FEATURES.remove();
        UNSAFE_WRITE_DEPTH.remove();
    }

    private static void enterUnsafeWrite() {
        Integer depth = UNSAFE_WRITE_DEPTH.get();
        UNSAFE_WRITE_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    private static void exitUnsafeWrite() {
        Integer depth = UNSAFE_WRITE_DEPTH.get();
        if (depth == null || depth <= 1) {
            UNSAFE_WRITE_DEPTH.remove();
        } else {
            UNSAFE_WRITE_DEPTH.set(depth - 1);
        }
    }
}
