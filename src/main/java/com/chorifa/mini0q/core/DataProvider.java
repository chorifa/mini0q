package com.chorifa.mini0q.core;

public interface DataProvider<T> {
    T get(long sequence);
}
