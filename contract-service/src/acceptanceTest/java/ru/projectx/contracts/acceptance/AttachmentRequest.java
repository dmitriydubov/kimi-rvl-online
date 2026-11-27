package ru.projectx.contracts.acceptance;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record AttachmentRequest(
        String path,
        Optional<String> attachmentPart,
        Optional<String> attachmentFilename,
        Optional<FilePart> filePart,
        Optional<String> requestId,
        boolean multipart) {

    static final String BOUNDARY = "stand-attachment-boundary";

    record FilePart(Optional<String> fileName, @Nullable String contentType, byte[] content) {
    }

    static AttachmentRequest certificate(String request, String participant, Optional<String> attachmentPart,
            Optional<FilePart> filePart, Optional<String> requestId, boolean multipart) {
        return new AttachmentRequest("/internal/v1/requests/" + request + "/participants/" + participant + "/attachments",
                attachmentPart, Optional.empty(), filePart, requestId, multipart);
    }

    static AttachmentRequest consent(String policy, String familyMember, Optional<String> attachmentPart,
            Optional<FilePart> filePart) {
        return new AttachmentRequest("/internal/v1/policies/" + policy + "/family-members/" + familyMember + "/consents",
                attachmentPart, Optional.empty(), filePart, Optional.empty(), true);
    }

    AttachmentRequest withRequestId(String value) {
        return new AttachmentRequest(path, attachmentPart, attachmentFilename, filePart, Optional.of(value), multipart);
    }

    AttachmentRequest withAttachmentFilename(String filename) {
        return new AttachmentRequest(path, attachmentPart, Optional.of(filename), filePart, requestId, multipart);
    }

    AttachmentRequest withFile(@Nullable String contentType, byte[] content, Optional<String> filename) {
        return new AttachmentRequest(path, attachmentPart, attachmentFilename,
                Optional.of(new FilePart(filename, contentType, content)), requestId, multipart);
    }

    AttachmentRequest withoutFile() {
        return new AttachmentRequest(path, attachmentPart, attachmentFilename, Optional.empty(), requestId, multipart);
    }

    AttachmentRequest withoutAttachment() {
        return new AttachmentRequest(path, Optional.empty(), attachmentFilename, filePart, requestId, multipart);
    }

    AttachmentRequest withAttachment(String attachment) {
        return new AttachmentRequest(path, Optional.of(attachment), attachmentFilename, filePart, requestId, multipart);
    }

    AttachmentRequest withPath(String replacement) {
        return new AttachmentRequest(replacement, attachmentPart, attachmentFilename, filePart, requestId, multipart);
    }

    String contentType() {
        return multipart ? "multipart/form-data; boundary=" + BOUNDARY : "application/json";
    }

    byte[] body() {
        if (!multipart) {
            return ("{\"attachment_id\":\"" + attachmentPart.orElse("") + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        attachmentPart.ifPresent(value -> text(body, "--" + BOUNDARY + "\r\nContent-Disposition: form-data; "
                + "name=\"attachment_id\"" + attachmentFilename.map(name -> "; filename=\"" + name + "\"").orElse("")
                + "\r\n\r\n" + value + "\r\n"));
        filePart.ifPresent(file -> file(body, file));
        text(body, "--" + BOUNDARY + "--\r\n");
        return body.toByteArray();
    }

    private static void file(ByteArrayOutputStream body, FilePart file) {
        text(body, "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\""
                + file.fileName().map(name -> "; filename=\"" + name + "\"").orElse("") + "\r\n"
                + Optional.ofNullable(file.contentType()).map(type -> "Content-Type: " + type + "\r\n").orElse("") + "\r\n");
        body.writeBytes(file.content());
        text(body, "\r\n");
    }

    private static void text(ByteArrayOutputStream body, String text) {
        body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
