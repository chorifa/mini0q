package com.chorifa.mini0q.core.consumer;

import com.chorifa.mini0q.core.AtomicLong;

public interface EventProcessor extends Runnable{

    AtomicLong getSequence();

    boolean isRunning();

    void stop();

}
