package cn.promptness.rpt.base.utils;


@FunctionalInterface
public interface Application<R> {
    R start(int value) throws Exception;
}