package com.chorifa.mini0q.core.wait;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.ThreadUtil;

public class BusySpinWaitStrategy implements WaitStrategy {

    @Override
    public long waitFor(long sequence, AtomicLong cursor, AtomicLong dependentSequence, SequenceBarrier barrier) throws AlertException {
        // cursor.get >= dependent.get
        long available;
        while ((available = dependentSequence.get()) < sequence){
            barrier.checkAlert();
            Thread.onSpinWait();
        }
        return available;
    }

    @Override
    public void signalAllWhenBlocking() {
        // do nothing
    }

}
