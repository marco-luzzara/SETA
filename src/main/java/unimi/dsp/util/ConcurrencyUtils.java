package unimi.dsp.util;

import unimi.dsp.taxi.NetworkTaxiConnection;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConcurrencyUtils {
    /**
     * execute n threads concurrently and call `join()` on each of them at the end
     * @param n number of threads to start
     * @param threadBody the code to execute for each thread. it accepts the index of the current iteration
     */
    public static void runThreadsConcurrentlyAndJoin(int n, Consumer<Integer> threadBody) {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Thread t = new ThreadWithIndex(i, threadBody);
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class ThreadWithIndex extends Thread {
        private final int i;
        private final Consumer<Integer> threadBody;

        public ThreadWithIndex(int i, Consumer<Integer> threadBody) {
            super();
            this.i = i;
            this.threadBody = threadBody;
        }

        @Override
        public void run() {
            this.threadBody.accept(this.i);
        }
    }
}
