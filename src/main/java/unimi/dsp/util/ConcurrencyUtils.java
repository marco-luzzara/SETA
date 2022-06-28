package unimi.dsp.util;

import unimi.dsp.taxi.NetworkTaxiConnection;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConcurrencyUtils {
    /**
     * https://stackoverflow.com/questions/743288/java-synchronization-utility
     * I did not choose a multiple-readers implementation because the risk of write
     * starvation is too high
     *
     * Utility that can take any object that implements a given interface and returns
     * a proxy that implements the same interface and synchronizes all calls that are
     * delegated to the given object. From Chris Jester-Young, http://about.me/cky
     * @param interfaceClass The interface to synchronize. Use MyInterface.class.
     * @param object The object to synchronize that implements the given interface class.
     * @return A synchronized proxy object that delegates to the given object.
     */
    public static <T> T makeSynchronized(Class<T> interfaceClass, final T object) {
        return interfaceClass.cast(
                Proxy.newProxyInstance(
                        object.getClass().getClassLoader(),
                        new Class<?>[]{interfaceClass},
                        (proxy, method, args) -> {
                            synchronized (object) {
                                return method.invoke(object, args);
                            }
                        }
                )
        );
    }

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
