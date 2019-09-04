package com.chorifa.mini0q.core.provider;

import com.chorifa.mini0q.core.AtomicLong;
import com.chorifa.mini0q.core.Cursored;
import com.chorifa.mini0q.core.SequenceBarrier;
import com.chorifa.mini0q.core.Sequenced;

public interface Sequencer extends Cursored, Sequenced {
    long INITIAL_CURSOR_VALUE = -1L;

    void claim(long sequence);

    boolean isAvailable(long sequence);

    void addGatingSequences(AtomicLong... gatingSequences);

    boolean removeGatingSequence(AtomicLong sequence);

    SequenceBarrier newBarrier(AtomicLong... sequencesToTrack);

    long getMinSequence();

    long getHighestPublishedSequence(long next, long available);
}
