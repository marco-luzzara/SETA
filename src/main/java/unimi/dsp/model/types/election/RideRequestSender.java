package unimi.dsp.model.types.election;

public interface RideRequestSender {
    void respondWithRetry(boolean mustRetry);
}
