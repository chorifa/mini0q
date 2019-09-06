package com.chorifa.mini0q.core.wait;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.Util;

import java.util.concurrent.locks.LockSupport;

public class BusySpinWaitStrategy implements WaitStrategy {

    @Override
    public long waitForProducer(long sequence, AtomicLong cursor, AtomicLong dependentSequence, SequenceBarrier barrier) throws AlertException {
        // cursor.get >= dependent.get
        long available;
        while ((available = dependentSequence.get()) < sequence){
            barrier.checkAlert();
            Thread.onSpinWait();
        }
        return available;
    }

    @Override
    public void signalAllConsumerWhenBlocking() {
        // do nothing
    }

    @Override
    public long waitForConsumer(long expected, AtomicLong[] gates, long current, int times) {
        long min = Util.getMinSequence(gates, current);
        if(expected > min){
            if(times < WaitStrategy.threshold)
                LockSupport.parkNanos(2L);//WaitStrategy.Wait_Times[waitTimes]);
            else
                LockSupport.parkNanos(WaitStrategy.Wait_Times[times - WaitStrategy.threshold]);
        }
        return min;
    }

    @Override
    public void signalAllProducerWhenBlocking() {
        // do nothing
    }

}
