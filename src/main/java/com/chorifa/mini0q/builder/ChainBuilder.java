package com.chorifa.mini0q.builder;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.RingQueue;
import com.chorifa.mini0q.core.consumer.EventHandler;
import com.chorifa.mini0q.utils.Assert;

import java.util.Arrays;

public class ChainBuilder<T> {

    private final RingQueueManager<T> builder;
    private AtomicLong[] currentLayerSequences;
    private AtomicLong[] previousLayerSequences;

    ChainBuilder(final RingQueueManager<T> builder, final AtomicLong[] currentLayerSequences, final AtomicLong[] previousLayerSequences){
        this.builder = builder;

        if(currentLayerSequences != null && currentLayerSequences.length != 0)
            this.currentLayerSequences = Arrays.copyOf(currentLayerSequences,currentLayerSequences.length);
        else this.currentLayerSequences = null;

        if(previousLayerSequences != null && previousLayerSequences.length != 0)
            this.previousLayerSequences = Arrays.copyOf(previousLayerSequences,previousLayerSequences.length);
        else this.previousLayerSequences = null;
    }

    @SafeVarargs
    public final ChainBuilder<T> andWithIndependent(EventHandler<? super T>... handlers){
        // same layer, delete all gating and add new all
        ChainBuilder<T> chain;
        if(previousLayerSequences == null) { // first layer
            chain = builder.handleEventIndependentWith(handlers);
        }else{
            chain = builder.createBatchProcessors(previousLayerSequences, handlers);
        }
        this.currentLayerSequences = combine(chain.currentLayerSequences, this.currentLayerSequences);
        return this;
    }

    @SafeVarargs
    public final ChainBuilder<T> thenWithIndependent(EventHandler<? super T>... handlers){
        // nothing to do with previousLayer
        return builder.createBatchProcessors(currentLayerSequences, handlers);
    }

    @SafeVarargs
    public final ChainBuilder<T> andWithInPool(EventHandler<? super T>... handlers){
        // same layer, delete all gating and add new all
        ChainBuilder<T> chain;
        if(previousLayerSequences == null) { // first layer
            chain = builder.handleEventInPoolWith(handlers);
        }else{
            chain = builder.createPoolProcessors(previousLayerSequences, handlers);
        }
        this.currentLayerSequences = combine(chain.currentLayerSequences, this.currentLayerSequences);
        return this;
    }

    @SafeVarargs
    public final ChainBuilder<T> thenWithInPool(EventHandler<? super T>... handlers){
        // nothing to do with previousLayer
        return builder.createPoolProcessors(currentLayerSequences, handlers);
    }

    public final ChainBuilder<T> thenWithInPool(final EventHandler<? super T> handler, int num){
        @SuppressWarnings("unchecked")
        EventHandler<? super T>[] handlers = new EventHandler[num];
        for(int i = 0; i < num; i++)
            handlers[i] = handler;
        return builder.createPoolProcessors(currentLayerSequences, handlers);
    }

    public final RingQueue<T> get(){
        return this.builder.get();
    }

    public final RingQueueManager<T> getManager(){
        return this.builder;
    }

    public final RingQueue<T> startAndGet(){
        return this.builder.startAndGet();
    }

    private AtomicLong[] combine(AtomicLong[] seq1, AtomicLong[] seq2){
        Assert.notEmpty(seq1);
        Assert.notEmpty(seq2);
        AtomicLong[] combined = new AtomicLong[seq1.length + seq2.length];
        System.arraycopy(seq1,0,combined,0,seq1.length);
        System.arraycopy(seq2,0,combined,seq1.length,seq2.length);
        return combined;
    }

}
