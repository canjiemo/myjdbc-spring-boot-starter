package io.github.canjiemo.base.myjdbc.lambda;

import java.util.regex.Pattern;

final class SqlFragmentGuard {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern DANGEROUS_SQL_KEYWORD = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|truncate|alter|create|grant|revoke|merge)\\b");

    private SqlFragmentGuard() {
    }

    static void requireSafeSqlFragment(String fragment, String scene) {
        if (fragment == null || fragment.isBlank()) {
            throw new IllegalArgumentException(scene + " 不能为空");
        }
        String normalized = fragment.trim();
        if (normalized.contains(";")
                || normalized.contains("--")
                || normalized.contains("/*")
                || normalized.contains("*/")) {
            throw new IllegalArgumentException(scene + " 包含不安全的 SQL 片段: " + fragment);
        }
        if (DANGEROUS_SQL_KEYWORD.matcher(normalized).find()) {
            throw new IllegalArgumentException(scene + " 包含危险关键字: " + fragment);
        }
    }

    static void requireSafeIdentifier(String identifier, String scene) {
        if (identifier == null || identifier.isBlank() || !SAFE_IDENTIFIER.matcher(identifier.trim()).matches()) {
            throw new IllegalArgumentException(scene + " 不是合法标识符: " + identifier);
        }
    }
}
