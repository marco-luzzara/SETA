package unimi.dsp.model.types.concurrency;

import java.util.ArrayDeque;
import java.util.Queue;

public class ThreadSafeQueue<T> {

    private final Queue<T> buffer = new ArrayDeque<>();

    public synchronized void put(T message) {
        buffer.add(message);
        notify();
    }

    public synchronized T take() throws InterruptedException {
        T message;

        while(buffer.size() == 0) {
            this.wait();
        }

        message = buffer.poll();

        return message;
    }

    public synchronized void removeAll() {
        buffer.clear();
    }
}

