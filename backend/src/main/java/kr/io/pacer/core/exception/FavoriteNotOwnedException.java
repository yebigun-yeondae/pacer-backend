package kr.io.pacer.core.exception;

public class FavoriteNotOwnedException extends RuntimeException {
    public FavoriteNotOwnedException(String message) {
        super(message);
    }
}
