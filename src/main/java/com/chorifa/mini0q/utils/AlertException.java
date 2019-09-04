package com.chorifa.mini0q.utils;

public class AlertException extends Exception {

    private AlertException(){}

    public static final AlertException INSTANCE = new AlertException();

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
