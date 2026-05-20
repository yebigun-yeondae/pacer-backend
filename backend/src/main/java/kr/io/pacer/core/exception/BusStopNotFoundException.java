package kr.io.pacer.core.exception;

public class BusStopNotFoundException extends RuntimeException {
    public BusStopNotFoundException(String message) {
        super(message);
    }
}
