package ru.projectx.contracts.service.storage;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

@Component
public class AttachmentRegistry {

    private final IssuanceRequestRepository requests;
    private final PrintedFormRepository documents;
    private final Clock clock;

    public AttachmentRegistry(IssuanceRequestRepository requests, PrintedFormRepository documents, Clock clock) {
        this.requests = requests;
        this.documents = documents;
        this.clock = clock;
    }

    @Transactional
    public void ensureOpen(AttachmentCommand upload) {
        ensureOpenFor(upload);
    }

    @Transactional
    public Optional<PrintedForm> accepted(AttachmentCommand upload, String fileHash) {
        ensureOpenFor(upload);
        return documents.findByAttachmentAttachmentId(upload.owner().attachmentId())
                .map(certificate -> sameUpload(certificate, upload, fileHash));
    }

    @Transactional
    public PrintedForm register(AttachmentCommand upload, StoredFile file) {
        ensureOpenFor(upload);
        return documents.saveAndFlush(PrintedForms.receivedAttachment(upload.owner(), upload.docType(), file,
                clock.instant()));
    }

    private void ensureOpenFor(AttachmentCommand upload) {
        UUID requestId = upload.owner().individualRequestId();
        if (requestId == null) {
            return;
        }
        requests.lockById(requestId);
        requests.findById(requestId).filter(IssuanceRequest::cancelled)
                .ifPresent(request -> {
                    throw new RequestCancelledException(requestId);
                });
    }

    private static PrintedForm sameUpload(PrintedForm certificate, AttachmentCommand upload, String fileHash) {
        boolean sameOwner = certificate.attachment().filter(upload.owner()::equals).isPresent();
        boolean sameType = certificate.docType() == upload.docType();
        boolean active = certificate.deletedAt().isEmpty();
        boolean sameContent = certificate.storedFile().map(StoredFile::hash).filter(fileHash::equals).isPresent();
        if (List.of(sameOwner, sameType, active, sameContent).contains(false)) {
            throw new AttachmentConflictException(upload.owner().attachmentId());
        }
        return certificate;
    }
}
