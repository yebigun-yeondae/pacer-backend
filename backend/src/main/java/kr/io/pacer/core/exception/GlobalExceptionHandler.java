package kr.io.pacer.core.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusStopNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBusStopNotFound(BusStopNotFoundException e) {
        log.warn("[Exception] BusStopNotFoundException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("BUS_STOP_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SubwayStationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSubwayStationNotFound(SubwayStationNotFoundException e) {
        log.warn("[Exception] SubwayStationNotFoundException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SUBWAY_STATION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(RouteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRouteNotFound(RouteNotFoundException e) {
        log.warn("[Exception] RouteNotFoundException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ROUTE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(FavoriteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFavoriteNotFound(FavoriteNotFoundException e) {
        log.warn("[Exception] FavoriteNotFoundException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("FAVORITE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(FavoriteNotOwnedException.class)
    public ResponseEntity<ErrorResponse> handleFavoriteNotOwned(FavoriteNotOwnedException e) {
        log.warn("[Exception] FavoriteNotOwnedException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FAVORITE_NOT_OWNED", e.getMessage()));
    }

    @ExceptionHandler(AlreadyWithdrawnException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyWithdrawn(AlreadyWithdrawnException e) {
        log.warn("[Exception] AlreadyWithdrawnException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ErrorResponse("ALREADY_WITHDRAWN", e.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException e) {
        log.warn("[Exception] InvalidTokenException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException e) {
        log.warn("[Exception] DuplicateEmailException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_EMAIL", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        log.warn("[Exception] InvalidCredentialsException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_CREDENTIALS", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[Exception] Validation 실패: {}", msg);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("[Exception] 처리되지 않은 예외 발생", e);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
