package unimi.dsp.adminServer.exceptions;

public class IdAlreadyRegisteredException extends Exception {
    private final int id;
    public IdAlreadyRegisteredException(int id) {
        this.id = id;
    }

    @Override
    public String getMessage() {
        return String.format("The id %d is already registered, choose a different taxi id", this.id);
    }
}
