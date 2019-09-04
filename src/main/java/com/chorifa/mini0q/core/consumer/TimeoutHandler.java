package com.chorifa.mini0q.core.consumer;

public interface TimeoutHandler {

    void onTimeout(long sequence) throws Exception;

}
