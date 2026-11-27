package ru.projectx.contracts.service.storage;

import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.StoredFile;

final class AttachmentResponses {

    private AttachmentResponses() {
    }

    static AttachmentResponse of(PrintedForm certificate) {
        ParticipantAttachment owner = certificate.attachment().orElseThrow(
                () -> new IllegalStateException("документ " + certificate.id() + " — не справка участника"));
        StoredFile file = certificate.storedFile().orElseThrow(
                () -> new IllegalStateException("у справки " + certificate.id() + " нет файла"));
        return new AttachmentResponse(certificate.id(), owner.attachmentId(), owner.individualRequestId(),
                owner.participantId(), file.contentType(), file.size(), file.hash(), certificate.createdAt());
    }

    static ConsentResponse ofConsent(PrintedForm certificate) {
        ParticipantAttachment owner = certificate.attachment().orElseThrow(
                () -> new IllegalStateException("документ " + certificate.id() + " — не согласие члена семьи"));
        StoredFile file = certificate.storedFile().orElseThrow(
                () -> new IllegalStateException("у согласия " + certificate.id() + " нет файла"));
        return new ConsentResponse(certificate.id(), owner.attachmentId(), owner.policyId(), owner.familyMemberId(),
                file.contentType(), file.size(), file.hash(), certificate.createdAt());
    }
}
