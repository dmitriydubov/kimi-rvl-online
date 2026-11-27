package ru.projectx.contracts.service.storage;

import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.PrintedFormType;

final class AttachmentCommands {

    private AttachmentCommands() {
    }

    static AttachmentCommand of(CertificateUpload upload) {
        return new AttachmentCommand(AttachmentOwners.certificate(upload.individualRequestId(), upload.participantId(),
                upload.attachmentId()), PrintedFormType.MEDICAL_CERTIFICATE, upload.contentType(), upload.size(),
                upload.content());
    }

    static AttachmentCommand of(ConsentUpload upload) {
        return new AttachmentCommand(AttachmentOwners.consent(upload.policyId(), upload.familyMemberId(),
                upload.attachmentId()), PrintedFormType.PD_CONSENT, upload.contentType(), upload.size(), upload.content());
    }
}
