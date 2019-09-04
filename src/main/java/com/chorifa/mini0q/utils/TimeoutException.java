package com.chorifa.mini0q.utils;

public class TimeoutException extends Exception {
    public static final TimeoutException INSTANCE = new TimeoutException();

    private TimeoutException(){}

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
