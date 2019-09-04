package com.chorifa.mini0q.core.provider;

import com.chorifa.mini0q.core.*;
import com.chorifa.mini0q.core.wait.WaitStrategy;
import com.chorifa.mini0q.utils.Assert;
import com.chorifa.mini0q.utils.CoreException;
import com.chorifa.mini0q.utils.Util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
//import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class AbstractSequencer implements Sequencer{

//    private static final AtomicReferenceFieldUpdater<AbstractSequencer, AtomicLong[]> GATING_UPDATER =
//            AtomicReferenceFieldUpdater.newUpdater(AbstractSequencer.class, AtomicLong[].class, "gatingSequences");

    private static final VarHandle GATING_HANDLE;

    static {
        try {
            GATING_HANDLE = MethodHandles.lookup().in(AbstractSequencer.class)
                    .findVarHandle(AbstractSequencer.class, "gatingSequences", AtomicLong[].class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new CoreException("AbstractSequencer: cannot get VarHandle for AtomicLong[].", e);
        }
    }

    protected final int bufferSize;
    protected final WaitStrategy waitStrategy;
    // all providers' cursor
    protected final AtomicLong cursor = new AtomicLong(Sequencer.INITIAL_CURSOR_VALUE);
    // all consumers' cursor
    protected volatile AtomicLong[] gatingSequences = new AtomicLong[0];

    protected AbstractSequencer(int bufferSize, WaitStrategy strategy){
        Assert.notNull(strategy);
        if(bufferSize <= 1) throw new CoreException("AbstractSequencer: bufferSize cannot less than 2");
        if(Integer.bitCount(bufferSize) != 1)
            this.bufferSize = Util.minPower2largerThan(bufferSize);
        else this.bufferSize = bufferSize;
        this.waitStrategy = strategy;
    }

    @Override
    public long getCursor() {
        return cursor.get();
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public void addGatingSequences(AtomicLong... gatingSequences) {
        AtomicLongGroups.addSequences(this,GATING_HANDLE,this,gatingSequences);
    }

    @Override
    public boolean removeGatingSequence(AtomicLong sequence) {
        return AtomicLongGroups.removeSequence(this, GATING_HANDLE, sequence);
    }

    @Override
    public SequenceBarrier newBarrier(AtomicLong... sequencesToTrack) {
        return new ProcessingSequenceBarrier(this, cursor, waitStrategy, sequencesToTrack);
    }

    @Override
    public long getMinSequence() {
        return Util.getMinSequence(gatingSequences, cursor.get());
    }

}
