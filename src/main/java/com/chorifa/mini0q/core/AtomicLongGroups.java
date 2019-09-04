package com.chorifa.mini0q.core;

import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class AtomicLongGroups {

    // Note: such method is thread-safe.
    // Once expectedSequences = updater.get(holder);
    // expectedSequences will not change by other thread.
    // caz it is an Array, and each time we compare and change the reference
    public static <T> void addSequences(
        final T holder,
        final AtomicReferenceFieldUpdater<T, AtomicLong[]> updater,
        final Cursored cursor,
        final AtomicLong... sequencesToAdd){
        long cursorSequence;
        AtomicLong[] newSequences;
        AtomicLong[] expectedSequences;
        do {
            expectedSequences = updater.get(holder);
            newSequences = Arrays.copyOf(expectedSequences,expectedSequences.length+sequencesToAdd.length);
            cursorSequence = cursor.getCursor();

            int index = expectedSequences.length;
            for(AtomicLong sequence : sequencesToAdd){
                sequence.lazySet(cursorSequence);
                newSequences[index++] = sequence;
            }
        }while (!updater.compareAndSet(holder,expectedSequences,newSequences));

        cursorSequence = cursor.getCursor();
        for(AtomicLong sequence: sequencesToAdd)
            sequence.lazySet(cursorSequence);
    }

    public static <T> void addSequences(
            final T holder,
            final VarHandle handle,
            final Cursored cursor,
            final AtomicLong... sequencesToAdd){
        long cursorSequence;
        AtomicLong[] newSequences;
        AtomicLong[] expectedSequences;
        do {
            expectedSequences = (AtomicLong[]) handle.getVolatile(holder);
            newSequences = Arrays.copyOf(expectedSequences,expectedSequences.length+sequencesToAdd.length);
            cursorSequence = cursor.getCursor();

            int index = expectedSequences.length;
            for(AtomicLong sequence : sequencesToAdd){
                sequence.lazySet(cursorSequence);
                newSequences[index++] = sequence;
            }
        }while (!handle.compareAndSet(holder,expectedSequences,newSequences));

        cursorSequence = cursor.getCursor();
        for(AtomicLong sequence: sequencesToAdd)
            sequence.lazySet(cursorSequence);
    }

    public static <T> boolean removeSequence(
        final T holder,
        final AtomicReferenceFieldUpdater<T,AtomicLong[]> updater,
        final AtomicLong sequence){
        int numToRemove;
        AtomicLong[] newSequences;
        AtomicLong[] expectedSequences;
        do {
            numToRemove = 0;
            expectedSequences = updater.get(holder);
            for(AtomicLong seq: expectedSequences){
                if(sequence == seq) numToRemove++;
            }
            if(numToRemove == 0) break;

            newSequences = new AtomicLong[expectedSequences.length - numToRemove];
            for(int i = 0, pos = 0; i < expectedSequences.length; i++){
                if(expectedSequences[i] != sequence)
                    newSequences[pos++] = expectedSequences[i];
            }
        }while (!updater.compareAndSet(holder,expectedSequences,newSequences));
        return numToRemove>0;
    }

    public static <T> boolean removeSequence(
            final T holder,
            final VarHandle handle,
            final AtomicLong sequence){
        int numToRemove;
        AtomicLong[] newSequences;
        AtomicLong[] expectedSequences;
        do {
            numToRemove = 0;
            expectedSequences = (AtomicLong[]) handle.getVolatile(holder);
            for(AtomicLong seq: expectedSequences){
                if(sequence == seq) numToRemove++;
            }
            if(numToRemove == 0) break;

            newSequences = new AtomicLong[expectedSequences.length - numToRemove];
            for(int i = 0, pos = 0; i < expectedSequences.length; i++){
                if(expectedSequences[i] != sequence)
                    newSequences[pos++] = expectedSequences[i];
            }
        }while (!handle.compareAndSet(holder,expectedSequences,newSequences));
        return numToRemove>0;
    }

}
