package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.unit.DataSize;
import ru.projectx.contracts.config.storage.AttachmentProperties;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.AttachmentRejectedException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.exception.StorageUnavailableException;

class AttachmentReceptionTest {

    private static final String KEY = "certificates/0b6c1d2e-7f3a-4b5c-9d8e-1f2a3b4c5d6e/" + Uploads.ATTACHMENT + ".jpg";
    private static final AttachmentResponse RESPONSE = new AttachmentResponse(UUID.randomUUID(), Uploads.ATTACHMENT,
            Uploads.REQUEST, Uploads.PARTICIPANT, "image/jpeg", 76, Uploads.CONTENT_HASH,
            Instant.parse("2026-02-10T07:05:00Z"));
    private static final PrintedForm PRIOR = prior();

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AttachmentMetrics metrics = new AttachmentMetrics(meters);
    private final AttachmentRegistry registry = mock(AttachmentRegistry.class);
    private final ObjectKeys keys = mock(ObjectKeys.class);
    private final CertificateStore store = mock(CertificateStore.class);
    private final AttachmentReception reception = new AttachmentReception(
            new AttachmentFiles(new AttachmentProperties(DataSize.ofMegabytes(5), Map.of("image/jpeg", "jpg")), metrics),
            registry, keys, store, metrics);

    @Test
    void newCertificate_isStoredUnderANewKeyThenRegisteredAndCounted() {
        CertificateUpload upload = Uploads.jpeg();
        when(registry.accepted(AttachmentCommands.of(upload), Uploads.CONTENT_HASH)).thenReturn(Optional.empty());
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);
        when(registry.register(any(AttachmentCommand.class), any(StoredFile.class))).thenReturn(PRIOR);

        assertThat(reception.receive(upload)).isEqualTo(RESPONSE);

        StoredFile expected = new StoredFile(KEY, Uploads.CONTENT_HASH, 76, "image/jpeg");
        InOrder order = inOrder(store, registry);
        order.verify(store).store(eq(expected), any(ContentSource.class));
        order.verify(registry).register(AttachmentCommands.of(upload), expected);
        assertThat(meters.get("contracts.attachments.stored").counter().count()).isEqualTo(1);
    }

    @Test
    void storedObject_receivesTheUploadedContent() throws IOException {
        CertificateUpload upload = Uploads.jpeg();
        when(registry.accepted(AttachmentCommands.of(upload), Uploads.CONTENT_HASH)).thenReturn(Optional.empty());
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);
        when(registry.register(any(AttachmentCommand.class), any(StoredFile.class))).thenReturn(PRIOR);

        reception.receive(upload);

        ArgumentCaptor<ContentSource> content = ArgumentCaptor.forClass(ContentSource.class);
        verify(store).store(any(StoredFile.class), content.capture());
        assertThat(content.getValue().open().readAllBytes()).isEqualTo(upload.content().open().readAllBytes());
    }

    @Test
    void repeatedAttachment_returnsThePriorRecordWithoutStoringOrCounting() {
        CertificateUpload upload = Uploads.jpeg();
        when(registry.accepted(AttachmentCommands.of(upload), Uploads.CONTENT_HASH)).thenReturn(Optional.of(PRIOR));

        assertThat(reception.receive(upload)).isEqualTo(RESPONSE);

        verifyNoInteractions(store, keys);
        verify(registry, never()).register(any(), any());
        assertThat(meters.find("contracts.attachments.stored").counter()).isNull();
    }

    @Test
    void rejectedFile_afterTheOpenCheckTouchesNeitherAcceptedLookupNorStorage() {
        CertificateUpload upload = Uploads.of(Uploads.PARTICIPANT, "image/png", Uploads.CONTENT);

        assertThatThrownBy(() -> reception.receive(upload)).isInstanceOf(AttachmentRejectedException.class);

        verify(registry).ensureOpen(AttachmentCommands.of(upload));
        verify(registry, never()).accepted(any(), any());
        verifyNoInteractions(store, keys);
    }

    @ParameterizedTest
    @MethodSource("invalidFiles")
    void cancelledRequest_winsOverFileValidationWithoutRejectedMetric(CertificateUpload upload) {
        doThrow(new RequestCancelledException(Uploads.REQUEST)).when(registry).ensureOpen(AttachmentCommands.of(upload));

        assertThatThrownBy(() -> reception.receive(upload)).isInstanceOf(RequestCancelledException.class);

        verify(registry, never()).accepted(any(), any());
        verifyNoInteractions(store, keys);
        assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
    }

    @Test
    void conflictingAttachment_isNotStored() {
        CertificateUpload upload = Uploads.jpeg();
        when(registry.accepted(AttachmentCommands.of(upload), Uploads.CONTENT_HASH)).thenThrow(new AttachmentConflictException(Uploads.ATTACHMENT));

        assertThatThrownBy(() -> reception.receive(upload)).isInstanceOf(AttachmentConflictException.class);

        verifyNoInteractions(store);
    }

    @Test
    void unavailableStorage_isCountedAndRethrownWithoutRegistering() {
        CertificateUpload upload = Uploads.jpeg();
        when(registry.accepted(AttachmentCommands.of(upload), Uploads.CONTENT_HASH)).thenReturn(Optional.empty());
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);
        StorageUnavailableException unavailable = new StorageUnavailableException(new IOException("refused"));
        doThrow(unavailable).when(store).store(any(), any());

        assertThatThrownBy(() -> reception.receive(upload)).isSameAs(unavailable);

        verify(registry, never()).register(any(), any());
        assertThat(meters.get("contracts.storage.unavailable").counter().count()).isEqualTo(1);
    }

    @Test
    void concurrentUploadOfTheSameAttachment_returnsTheWinningRecordWithoutCounting() {
        CertificateUpload upload = Uploads.jpeg();
        when(registry.accepted(AttachmentCommands.of(upload), Uploads.CONTENT_HASH)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(PRIOR));
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);
        when(registry.register(any(AttachmentCommand.class), any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertThat(reception.receive(upload)).isEqualTo(RESPONSE);

        assertThat(meters.find("contracts.attachments.stored").counter()).isNull();
    }

    private static Stream<CertificateUpload> invalidFiles() {
        return Stream.of(Uploads.sized("image/gif", 76), Uploads.sized("image/png", 0),
                Uploads.sized("image/png", 5_242_881));
    }

    private static PrintedForm prior() {
        PrintedForm form = mock(PrintedForm.class);
        when(form.id()).thenReturn(RESPONSE.documentId());
        when(form.attachment()).thenReturn(Optional.of(AttachmentOwners.certificate(
                Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT)));
        when(form.storedFile()).thenReturn(Optional.of(new StoredFile(KEY, Uploads.CONTENT_HASH, 76, "image/jpeg")));
        when(form.createdAt()).thenReturn(RESPONSE.createdAt());
        return form;
    }

    @Test
    void integrityViolationWithoutAWinningRecord_isRethrown() {
        CertificateUpload upload = Uploads.jpeg();
        when(registry.accepted(AttachmentCommands.of(upload), Uploads.CONTENT_HASH)).thenReturn(Optional.empty());
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("fk");
        when(registry.register(any(AttachmentCommand.class), any())).thenThrow(violation);

        assertThatThrownBy(() -> reception.receive(upload)).isSameAs(violation);
    }
}
