package ru.projectx.contracts.controller;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.projectx.contracts.dto.ErrorResponse;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.AttachmentDefect;
import ru.projectx.contracts.exception.AttachmentRejectedException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.exception.RequestNotFoundException;
import ru.projectx.contracts.exception.StorageUnavailableException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DomainErrorAdvice {

    private static final Map<AttachmentDefect, String> DEFECT_REASONS = new EnumMap<>(Map.of(
            AttachmentDefect.CONTENT_TYPE, "тип файла не допускается",
            AttachmentDefect.SIZE, "размер файла вне допустимого"));

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> requestNotFound(RequestNotFoundException notFound) {
        return ApiErrors.of(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "задачи на выпуск по заявке нет");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> requestCancelled(RequestCancelledException cancelled) {
        return ApiErrors.of(HttpStatus.CONFLICT, "INVALID_STATE", "работа по заявке прекращена");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> attachmentConflict(AttachmentConflictException conflict) {
        return ApiErrors.of(HttpStatus.CONFLICT, "ATTACHMENT_CONFLICT",
                "вложение уже принято с другим содержимым, владельцем или видом либо удалено");
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> attachmentRejected(AttachmentRejectedException rejected) {
        return ApiErrors.invalidField(ApiErrors.FILE_FIELD, DEFECT_REASONS.get(rejected.defect()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> storageUnavailable(StorageUnavailableException unavailable) {
        return ApiErrors.of(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "хранилище файлов недоступно");
    }
}
