package com.chorifa.mini0q.core.consumer;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.RingQueue;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.core.provider.Sequencer;
import com.chorifa.mini0q.utils.CoreException;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConcurrentProcessorPool<T> {

    private final AtomicBoolean startLock = new AtomicBoolean(false);
    private final AtomicLong poolSequence = new AtomicLong(Sequencer.INITIAL_CURSOR_VALUE);
    private final RingQueue<T> ringQueue;
    private final ConcurrentEventProcessor<?>[] concurrentEventProcessors;

    @SafeVarargs
    public ConcurrentProcessorPool(
            final RingQueue<T> provider,
            final SequenceBarrier barrier,
            final EventHandler<? super T>... eventHandlers){
        this.ringQueue = provider;
        int numProcessors = eventHandlers.length;
        concurrentEventProcessors = new ConcurrentEventProcessor[numProcessors];
        for(int i = 0; i < numProcessors; i++)
            concurrentEventProcessors[i] = new ConcurrentEventProcessor<T>(
              i,provider, barrier , eventHandlers[i] , poolSequence);
    }

    public AtomicLong[] getSequencesInPool(){
        AtomicLong[] sequences = new AtomicLong[concurrentEventProcessors.length+1];
        for(int i = 0; i < concurrentEventProcessors.length; i++)
            sequences[i] = concurrentEventProcessors[i].getSequence();
        sequences[concurrentEventProcessors.length] = poolSequence;
        return sequences;
    }

    public RingQueue<T> start(final Executor executor){
        if(startLock.compareAndSet(false,true)){
            long cursor = ringQueue.getCursor();
            poolSequence.volatileSet(cursor);
            for(ConcurrentEventProcessor<?> processor : concurrentEventProcessors){
                processor.getSequence().volatileSet(cursor);
            }
            for(ConcurrentEventProcessor<?> processor : concurrentEventProcessors){
                executor.execute(processor);
            }
            return ringQueue;
        }
        throw new CoreException("ConcurrentProcessorPool: pool start error in current thread");
    }

    public void stop(){
        for(ConcurrentEventProcessor<?> processor : concurrentEventProcessors){
            processor.stop();
        }
    }

}
