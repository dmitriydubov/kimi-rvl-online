package ru.projectx.contracts.service.storage;

import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.document.PrintedFormType;

public record AttachmentCommand(
        ParticipantAttachment owner,
        PrintedFormType docType,
        @Nullable String contentType,
        long size,
        ContentSource content) {
}
