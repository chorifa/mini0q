package com.chorifa.mini0q.core;

import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.TimeoutException;

public interface SequenceBarrier {

    long waitFor(long sequence) throws TimeoutException, InterruptedException, AlertException;

    long getCursor();

    void checkAlert() throws AlertException;

    void alert();

    void notifyProducer();
}
