package ru.projectx.contracts.service.storage;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.StorageUnavailableException;

@Service
public class AttachmentReception implements AttachmentUploads, ConsentUploads {

    private final AttachmentFiles files;
    private final AttachmentRegistry registry;
    private final ObjectKeys keys;
    private final CertificateStore store;
    private final AttachmentMetrics metrics;

    public AttachmentReception(AttachmentFiles files, AttachmentRegistry registry, ObjectKeys keys,
            CertificateStore store, AttachmentMetrics metrics) {
        this.files = files;
        this.registry = registry;
        this.keys = keys;
        this.store = store;
        this.metrics = metrics;
    }

    @Override
    public AttachmentResponse receive(CertificateUpload upload) {
        return AttachmentResponses.of(receive(AttachmentCommands.of(upload)));
    }

    @Override
    public ConsentResponse receive(ConsentUpload upload) {
        return AttachmentResponses.ofConsent(receive(AttachmentCommands.of(upload)));
    }

    private PrintedForm receive(AttachmentCommand upload) {
        AttachmentFiles.AcceptedFile accepted = files.accept(upload);
        String fileHash = ContentDigests.sha256(upload.content());
        return registry.accepted(upload, fileHash).orElseGet(() -> stored(upload, new StoredFile(
                keys.next(upload.owner().attachmentId(), accepted.extension()), fileHash, upload.size(),
                accepted.contentType())));
    }

    private PrintedForm stored(AttachmentCommand upload, StoredFile file) {
        try {
            store.store(file, upload.content());
        } catch (StorageUnavailableException unavailable) {
            metrics.countStorageUnavailable();
            throw unavailable;
        }
        return registered(upload, file);
    }

    private PrintedForm registered(AttachmentCommand upload, StoredFile file) {
        try {
            PrintedForm response = registry.register(upload, file);
            metrics.countStored();
            return response;
        } catch (DataIntegrityViolationException concurrentUpload) {
            return registry.accepted(upload, file.hash()).orElseThrow(() -> concurrentUpload);
        }
    }
}
