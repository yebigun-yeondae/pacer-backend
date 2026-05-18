package kr.io.pacer.core.exception;

public class AlreadyWithdrawnException extends RuntimeException {
    public AlreadyWithdrawnException(String message) {
        super(message);
    }
}
