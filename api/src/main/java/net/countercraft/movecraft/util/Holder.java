package net.countercraft.movecraft.util;

public class Holder<T> {

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public boolean isEmpty() {
        return this.value == null;
    }

    private T value;

    public Holder() {
        this.value = null;
    }
    public Holder(T value) {
        this.value = value;
    }

    public void clear() {
        this.value = null;
    }
}
