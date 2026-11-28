package ru.projectx.contracts.exception;

import java.util.UUID;

public class AttachmentConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttachmentConflictException(UUID attachmentId) {
        super("вложение " + attachmentId + " уже принято с другим содержимым, владельцем или видом либо удалено");
    }
}
