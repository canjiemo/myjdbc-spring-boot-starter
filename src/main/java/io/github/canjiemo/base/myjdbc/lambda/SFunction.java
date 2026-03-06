package io.github.canjiemo.base.myjdbc.lambda;

@FunctionalInterface
public interface SFunction<T, R> extends java.util.function.Function<T, R>, java.io.Serializable {}
