package com.centrika.orderapi.dto;

import java.time.Instant;
import java.util.List;

public class ApiErrorResponse {
    private Instant timestamp = Instant.now();
    private int status;
    private String error;
    private String message;
    private List<FieldErrorDetail> fieldErrors;

    public ApiErrorResponse(int status, String error, String message, List<FieldErrorDetail> fieldErrors) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.fieldErrors = fieldErrors;
    }

    public Instant getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public List<FieldErrorDetail> getFieldErrors() { return fieldErrors; }

    public static class FieldErrorDetail {
        private String field;
        private String message;

        public FieldErrorDetail(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
    }
}
