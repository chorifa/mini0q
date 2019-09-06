package com.chorifa.mini0q.core;

import com.chorifa.mini0q.core.producer.Sequencer;
import com.chorifa.mini0q.core.wait.WaitStrategy;
import com.chorifa.mini0q.utils.AlertException;
import com.chorifa.mini0q.utils.TimeoutException;

/**
 * used for consumers
 */
public class ProcessingSequenceBarrier implements SequenceBarrier{

    private final WaitStrategy waitStrategy;
    private final AtomicLong dependentSequence;
    private final Sequencer provider;
    private final AtomicLong providerCursor;

    private volatile boolean stop = false;

    public ProcessingSequenceBarrier(Sequencer provider, AtomicLong providerCursor, WaitStrategy waitStrategy, AtomicLong[] dependentSequences) {
        this.waitStrategy = waitStrategy;
        this.provider = provider;
        this.providerCursor = providerCursor;
        if(dependentSequences == null || dependentSequences.length == 0) dependentSequence = providerCursor;
        else dependentSequence = new AggregatedAtomicLong(dependentSequences);
    }

    @Override
    public long waitFor(long sequence) throws TimeoutException, InterruptedException, AlertException{
        checkAlert();
        long available = waitStrategy.waitForProducer(sequence, providerCursor, dependentSequence, this);
        if(available < sequence) return available;
        return provider.getHighestPublishedSequence(sequence, available);
    }

    @Override
    public void notifyProducer() {
        waitStrategy.signalAllProducerWhenBlocking();
    }

    public void alert(){
        stop = true;
    }

    public void checkAlert() throws AlertException {
        if(stop) throw AlertException.INSTANCE;
    }

    @Override
    public long getCursor() {
        return dependentSequence.get();
    }

}
