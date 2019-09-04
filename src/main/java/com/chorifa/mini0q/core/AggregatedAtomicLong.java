package com.chorifa.mini0q.core;

import com.chorifa.mini0q.utils.Util;

import java.util.Arrays;

public class AggregatedAtomicLong extends AtomicLong {

    private final AtomicLong[] atomicLongs;

    public AggregatedAtomicLong(AtomicLong[] atomicLongs){
        this.atomicLongs = Arrays.copyOf(atomicLongs, atomicLongs.length);
    }

    @Override
    public long get() {
        return Util.getMinSequence(atomicLongs);
    }

    @Override
    public String toString() {
        return Arrays.toString(atomicLongs);
    }

    @Override
    public void lazySet(long newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void volatileSet(long newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean compareAndSet(long expectedValue, long newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long addAndGet(long delta) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getAndAdd(long delta) {
        throw new UnsupportedOperationException();
    }
}
