package unimi.dsp.sensors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class SlidingWindowBuffer implements Buffer {
    private final int slidingWindowBufferSize;
    private final float slidingWindowOverlappingFactor;
    private final Queue<Measurement> buffer;

    public SlidingWindowBuffer(int slidingWindowBufferSize, float slidingWindowOverlappingFactor) {
        this.slidingWindowBufferSize = slidingWindowBufferSize;
        this.slidingWindowOverlappingFactor = slidingWindowOverlappingFactor;
        this.buffer = new ArrayDeque<>(this.slidingWindowBufferSize);
    }

    @Override
    public synchronized void addMeasurement(Measurement m) {
        try {
            while (buffer.size() == this.slidingWindowBufferSize) {
                this.wait();
            }

            this.buffer.add(m);
            if (buffer.size() == this.slidingWindowBufferSize)
                this.notify();
        }
        catch (InterruptedException e) {
            // InterruptedException is thrown only while waiting for this.wait(),
            // so notifying is useless
        }
    }

    @Override
    public synchronized List<Measurement> readAllAndClean() {
        try {
            while (buffer.size() < this.slidingWindowBufferSize) {
                this.wait();
            }

            List<Measurement> windowMeasurements = new ArrayList<>(this.buffer);

            int numberOfRemoves = Math.round(this.slidingWindowBufferSize * this.slidingWindowOverlappingFactor);
            for (int i = 0; i < numberOfRemoves; i++)
                this.buffer.remove();

            this.notify();

            return windowMeasurements;
        } catch (InterruptedException e) {
            return new ArrayList<>(this.buffer);
        }
    }
}
