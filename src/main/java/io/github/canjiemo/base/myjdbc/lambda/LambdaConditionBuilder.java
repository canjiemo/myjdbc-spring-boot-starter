package io.github.canjiemo.base.myjdbc.lambda;

import io.github.canjiemo.base.myjdbc.MyTableEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LambdaConditionBuilder<T extends MyTableEntity> {

    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":(\\w+)");

    private final Class<T> entityClazz;
    private final Map<String, Object> params;
    private final AtomicInteger paramIndex;
    private final List<ConditionNode> nodes = new ArrayList<>();
    private String nextConnector = "AND";

    LambdaConditionBuilder(Class<T> entityClazz) {
        this(entityClazz, new LinkedHashMap<>(), new AtomicInteger());
    }

    private LambdaConditionBuilder(Class<T> entityClazz, Map<String, Object> params, AtomicInteger paramIndex) {
        this.entityClazz = entityClazz;
        this.params = params;
        this.paramIndex = paramIndex;
    }

    boolean isEmptyValue(Object val) {
        if (val == null) {
            return true;
        }
        if (val instanceof String s) {
            return s.trim().isEmpty();
        }
        if (val instanceof Collection<?> c) {
            return c.isEmpty();
        }
        return false;
    }

    String column(SFunction<T, ?> fn) {
        return LambdaUtils.getColumnName(fn, entityClazz);
    }

    String bindValue(Object value) {
        String paramName = nextParamName();
        params.put(paramName, value);
        return ":" + paramName;
    }

    String bindFragment(String fragment, Map<String, ?> values, String scene) {
        SqlFragmentGuard.requireSafeSqlFragment(fragment, scene);
        String normalized = fragment.trim();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(normalized);
        StringBuffer buffer = new StringBuffer();
        Map<String, String> renamed = new LinkedHashMap<>();

        while (matcher.find()) {
            String originalName = matcher.group(1);
            if (values == null || !values.containsKey(originalName)) {
                throw new IllegalArgumentException(scene + " 缺少参数: " + originalName);
            }
            String rewrittenName = renamed.computeIfAbsent(originalName, key -> {
                String newName = nextParamName();
                params.put(newName, values.get(key));
                return newName;
            });
            matcher.appendReplacement(buffer, ":" + rewrittenName);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    String normalizeFragment(String fragment, String scene) {
        SqlFragmentGuard.requireSafeSqlFragment(fragment, scene);
        return fragment.trim();
    }

    void nextOr() {
        nextConnector = "OR";
    }

    void addCondition(String expression) {
        addCondition(null, expression);
    }

    void addCondition(String explicitConnector, String expression) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        String connector = nodes.isEmpty() ? null : (explicitConnector != null ? explicitConnector : nextConnector);
        nodes.add(new ConditionNode(connector, expression.trim()));
        nextConnector = "AND";
    }

    void addNestedGroup(String explicitConnector, Consumer<LambdaFilter<T>> consumer) {
        if (consumer == null) {
            return;
        }
        LambdaConditionBuilder<T> child = createChild();
        consumer.accept(new LambdaNestedFilter<>(entityClazz, child));
        String rendered = child.render();
        if (!rendered.isBlank()) {
            addCondition(explicitConnector, "(" + rendered + ")");
        }
    }

    @SafeVarargs
    final void addComposedGroup(String groupConnector, Consumer<LambdaFilter<T>>... consumers) {
        if (consumers == null || consumers.length == 0) {
            return;
        }
        List<String> groups = new ArrayList<>();
        for (Consumer<LambdaFilter<T>> consumer : consumers) {
            if (consumer == null) {
                continue;
            }
            LambdaConditionBuilder<T> child = createChild();
            consumer.accept(new LambdaNestedFilter<>(entityClazz, child));
            String rendered = child.render();
            if (!rendered.isBlank()) {
                groups.add("(" + rendered + ")");
            }
        }
        if (groups.isEmpty()) {
            return;
        }
        if (groups.size() == 1) {
            addCondition(groups.get(0));
            return;
        }
        addCondition("(" + String.join(" " + groupConnector + " ", groups) + ")");
    }

    String render() {
        StringBuilder builder = new StringBuilder();
        for (ConditionNode node : nodes) {
            if (builder.length() > 0) {
                builder.append(' ').append(node.connector()).append(' ');
            }
            builder.append(node.expression());
        }
        return builder.toString();
    }

    Map<String, Object> getParamsView() {
        return java.util.Collections.unmodifiableMap(params);
    }

    private LambdaConditionBuilder<T> createChild() {
        return new LambdaConditionBuilder<>(entityClazz, params, paramIndex);
    }

    private String nextParamName() {
        return "lwp" + paramIndex.getAndIncrement();
    }

    private record ConditionNode(String connector, String expression) {
    }
}
