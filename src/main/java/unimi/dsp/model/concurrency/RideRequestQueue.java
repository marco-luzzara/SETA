package unimi.dsp.model.concurrency;

import unimi.dsp.dto.RideRequestDto;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

public class RideRequestQueue {
    private final Queue<RideRequestDto> queue = new LinkedList<>();

    /**
     * the ride request is removed only if the head of the queue contains the current id resource,
     * otherwise the ride request has already been approved.
     * @param id
     * @return `Optional.Empty` if the ride confirm is not valid, the ride request if it is valid
     */
    public synchronized Optional<RideRequestDto> remove(int id) {
        while (queue.size() == 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        RideRequestDto headOfQueue = this.queue.peek();
        if (headOfQueue.getId() == id)
            return Optional.of(queue.remove());
        else
            return Optional.empty();
    }

    public synchronized void add(RideRequestDto rideRequest) {
        queue.add(rideRequest);
        notify();
    }

    public int size() {
        return queue.size();
    }
}
