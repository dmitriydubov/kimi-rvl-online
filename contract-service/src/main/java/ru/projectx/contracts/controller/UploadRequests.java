package ru.projectx.contracts.controller;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import ru.projectx.contracts.service.storage.CertificateUpload;
import ru.projectx.contracts.service.storage.ConsentUpload;

final class UploadRequests {

    private UploadRequests() {
    }

    static CertificateUpload certificate(UUID request, UUID participant, UUID attachment, MultipartFile file) {
        return new CertificateUpload(request, participant, attachment, file.getContentType(), file.getSize(),
                file::getInputStream);
    }

    static ConsentUpload consent(UUID policy, UUID familyMember, UUID attachment, MultipartFile file) {
        return new ConsentUpload(policy, familyMember, attachment, file.getContentType(), file.getSize(),
                file::getInputStream);
    }
}
