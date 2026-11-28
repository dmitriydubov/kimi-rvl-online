package ru.projectx.contracts.controller;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import ru.projectx.contracts.exception.AttachmentFormatException;
import ru.projectx.contracts.service.Guids;
import ru.projectx.contracts.service.storage.CertificateUpload;
import ru.projectx.contracts.service.storage.ConsentUpload;

final class UploadRequests {

    private UploadRequests() {
    }

    static CertificateUpload certificate(String request, String participant, String attachment, MultipartFile file) {
        return new CertificateUpload(uuid(request, "individual_request_id"), uuid(participant, "participant_id"),
                uuid(attachment, "attachment_id"), file.getContentType(), file.getSize(), file::getInputStream);
    }

    static ConsentUpload consent(String policy, String familyMember, String attachment, MultipartFile file) {
        return new ConsentUpload(uuid(policy, "policy_id"), uuid(familyMember, "family_member_id"),
                uuid(attachment, "attachment_id"), file.getContentType(), file.getSize(), file::getInputStream);
    }

    private static UUID uuid(String value, String field) {
        return Guids.canonical(value).orElseThrow(() -> new AttachmentFormatException(field));
    }
}
