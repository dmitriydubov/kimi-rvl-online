package ru.projectx.contracts.exception;

public class AttachmentFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public AttachmentFormatException(String field) {
        super("неверный формат части " + field);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
