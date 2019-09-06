package com.chorifa.mini0q.core.wait;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.TimeoutException;

public interface WaitStrategy {
    int threshold = 50;

    long[] Wait_Times = {4L, 16L, 128L, 256L, 512L, 1024L, 2048L, 2048L, 4096L, 4096L, 8192L, 16384L, 1000_1000L};

    long waitForProducer(long sequence, AtomicLong cursor, AtomicLong dependentSequence, SequenceBarrier barrier) throws TimeoutException, InterruptedException, AlertException;

    void signalAllConsumerWhenBlocking();

    long waitForConsumer(long expected, AtomicLong[] gates, long current, int times) throws InterruptedException;

    void signalAllProducerWhenBlocking();
}
