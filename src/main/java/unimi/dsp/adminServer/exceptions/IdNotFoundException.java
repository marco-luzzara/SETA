package unimi.dsp.adminServer.exceptions;

public class IdNotFoundException extends Exception {
    private final int id;

    public IdNotFoundException(int id) {
        this.id = id;
    }

    @Override
    public String getMessage() {
        return String.format("Cannot find taxi with id = %d", this.id);
    }
}
