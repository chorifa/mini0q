package com.chorifa.mini0q.core.wait;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.TimeoutException;

public class BlockingWaitStrategy implements WaitStrategy {

    private final Object lock = new Object();
    private final long timeoutInNanos;
    private final boolean allowTimeout;

    public BlockingWaitStrategy(long timeoutInNanos) {
        this.timeoutInNanos = timeoutInNanos;
        this.allowTimeout = true;
    }

    public BlockingWaitStrategy() {
        this.allowTimeout = false;
        this.timeoutInNanos = 1000*1000*1000; // 1s
    }

    @Override
    public long waitFor(long sequence, AtomicLong cursor, AtomicLong dependentSequence, SequenceBarrier barrier) throws TimeoutException, InterruptedException, AlertException {
        long available; long start;
        if(cursor.get() < sequence){
            synchronized (lock){
                while (cursor.get() < sequence){
                    barrier.checkAlert();

                    start = System.nanoTime();
                    lock.wait(timeoutInNanos/1000000, (int)(timeoutInNanos%1000000)); // wait 1s , not timeout
                    if(allowTimeout && (System.nanoTime()-start) > timeoutInNanos)
                        throw TimeoutException.INSTANCE;
                }
            }
        }
        // busy-waiting
        while ((available = dependentSequence.get()) < sequence){
            barrier.checkAlert();
            Thread.onSpinWait();
        }

        return available;
    }

    @Override
    public void signalAllWhenBlocking() {
        synchronized (lock){
            lock.notifyAll();
        }
    }

}
