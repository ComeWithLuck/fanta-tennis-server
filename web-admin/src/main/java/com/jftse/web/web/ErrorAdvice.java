package com.jftse.web.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** A bad/missing CSRF token means the POST did not come from our own form: answer 400, not a stack trace. */
@ControllerAdvice
public class ErrorAdvice {
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> badRequest(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("คำขอไม่ถูกต้อง (" + e.getMessage() + ") — กรุณาโหลดหน้าใหม่แล้วลองอีกครั้ง");
    }

    /** A moderator tried something only a GM may do. */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> forbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("ไม่มีสิทธิ์: " + e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ค่าไม่ถูกต้อง: " + e.getMessage());
    }
}
