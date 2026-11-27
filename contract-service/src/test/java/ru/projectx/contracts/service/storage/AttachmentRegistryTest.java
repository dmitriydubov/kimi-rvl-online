package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

class AttachmentRegistryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final StoredFile FILE = new StoredFile("certificates/f/" + Uploads.ATTACHMENT + ".jpg",
            Uploads.CONTENT_HASH, 76, "image/jpeg");

    private final IssuanceRequestRepository requests = mock(IssuanceRequestRepository.class);
    private final PrintedFormRepository documents = mock(PrintedFormRepository.class);
    private final AttachmentRegistry registry = new AttachmentRegistry(requests, documents,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void unknownRequest_hasNoAcceptedRecord() {
        when(requests.findById(Uploads.REQUEST)).thenReturn(Optional.empty());
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.empty());

        assertThat(registry.accepted(AttachmentCommands.of(Uploads.jpeg()), Uploads.CONTENT_HASH).map(AttachmentResponses::of)).isEmpty();
    }

    @Test
    void participantOutsideTheComposition_hasNoAcceptedRecord() {
        knownRequest(false, Uploads.OTHER_PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.empty());

        assertThat(registry.accepted(AttachmentCommands.of(Uploads.jpeg()), Uploads.CONTENT_HASH).map(AttachmentResponses::of)).isEmpty();
    }

    @Test
    void cancelledRequest_isRequestCancelledEvenForItsParticipant() {
        knownRequest(true, Uploads.PARTICIPANT);

        assertThatThrownBy(() -> registry.accepted(AttachmentCommands.of(Uploads.jpeg()), Uploads.CONTENT_HASH).map(AttachmentResponses::of))
                .isInstanceOf(RequestCancelledException.class).hasMessageContaining(Uploads.REQUEST.toString());
    }

    @Test
    void newAttachmentOfAParticipant_hasNoAcceptedRecord() {
        knownRequest(false, Uploads.OTHER_PARTICIPANT, Uploads.PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.empty());

        assertThat(registry.accepted(AttachmentCommands.of(Uploads.jpeg()), Uploads.CONTENT_HASH).map(AttachmentResponses::of)).isEmpty();
    }

    @Test
    void sameAttachmentWithSameContent_isThePriorRecord() {
        knownRequest(false, Uploads.PARTICIPANT);
        PrintedForm prior = certificate(Uploads.PARTICIPANT, Uploads.CONTENT_HASH);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(prior));

        assertThat(registry.accepted(AttachmentCommands.of(Uploads.jpeg()), Uploads.CONTENT_HASH).map(AttachmentResponses::of)).get()
                .returns(Uploads.ATTACHMENT, AttachmentResponse::attachmentId)
                .returns(NOW.minusSeconds(300), AttachmentResponse::createdAt);
    }

    @Test
    void sameAttachmentWithOtherContent_isConflict() {
        knownRequest(false, Uploads.PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT))
                .thenReturn(Optional.of(certificate(Uploads.PARTICIPANT, "0".repeat(64))));

        assertThatThrownBy(() -> registry.accepted(AttachmentCommands.of(Uploads.jpeg()), Uploads.CONTENT_HASH).map(AttachmentResponses::of))
                .isInstanceOf(AttachmentConflictException.class).hasMessageContaining(Uploads.ATTACHMENT.toString());
    }

    @Test
    void sameAttachmentOfAnotherParticipant_isConflict() {
        knownRequest(false, Uploads.PARTICIPANT, Uploads.OTHER_PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT))
                .thenReturn(Optional.of(certificate(Uploads.OTHER_PARTICIPANT, Uploads.CONTENT_HASH)));

        assertThatThrownBy(() -> registry.accepted(AttachmentCommands.of(Uploads.jpeg()), Uploads.CONTENT_HASH).map(AttachmentResponses::of))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void consent_doesNotReadTheIssuanceRequest() {
        ConsentUpload upload = new ConsentUpload(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT,
                "image/jpeg", 76, Uploads.jpeg().content());
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.empty());

        assertThat(registry.accepted(AttachmentCommands.of(upload), Uploads.CONTENT_HASH)).isEmpty();

        verifyNoInteractions(requests);
    }

    @Test
    void sameOwnerWithOtherAttachmentKind_isConflict() {
        PrintedForm consent = mock(PrintedForm.class);
        when(consent.attachment()).thenReturn(Optional.of(AttachmentOwners.certificate(
                Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT)));
        when(consent.docType()).thenReturn(PrintedFormType.PD_CONSENT);
        when(consent.deletedAt()).thenReturn(Optional.empty());
        when(consent.storedFile()).thenReturn(Optional.of(FILE));
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(consent));

        assertThatThrownBy(() -> registry.accepted(AttachmentCommands.of(Uploads.jpeg()), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void deletedAttachment_isConflictEvenWithTheSameContent() {
        PrintedForm deleted = mock(PrintedForm.class);
        when(deleted.attachment()).thenReturn(Optional.of(AttachmentOwners.certificate(
                Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT)));
        when(deleted.docType()).thenReturn(PrintedFormType.MEDICAL_CERTIFICATE);
        when(deleted.deletedAt()).thenReturn(Optional.of(NOW));
        when(deleted.storedFile()).thenReturn(Optional.of(FILE));
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> registry.accepted(AttachmentCommands.of(Uploads.jpeg()), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void register_savesTheCertificateOfTheParticipantAtTheCurrentInstant() {
        knownRequest(false, Uploads.PARTICIPANT);
        when(documents.saveAndFlush(any(PrintedForm.class))).thenAnswer(saved -> saved.getArgument(0));

        AttachmentResponse response = AttachmentResponses.of(registry.register(AttachmentCommands.of(Uploads.jpeg()), FILE));

        ArgumentCaptor<PrintedForm> saved = ArgumentCaptor.forClass(PrintedForm.class);
        verify(documents).saveAndFlush(saved.capture());
        assertThat(saved.getValue().attachment())
                .contains(AttachmentOwners.certificate(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT));
        assertThat(saved.getValue().storedFile()).contains(FILE);
        assertThat(saved.getValue().createdAt()).isEqualTo(NOW);
        assertThat(response.fileHash()).isEqualTo(Uploads.CONTENT_HASH);
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void requestCancelledWhileTheFileWasStored_isNotRegistered() {
        knownRequest(true, Uploads.PARTICIPANT);

        assertThatThrownBy(() -> AttachmentResponses.of(registry.register(AttachmentCommands.of(Uploads.jpeg()), FILE))).isInstanceOf(RequestCancelledException.class);

        verify(documents, never()).saveAndFlush(any());
    }

    @Test
    void participantExcludedWhileTheFileWasStored_isStillRegistered() {
        knownRequest(false, Uploads.OTHER_PARTICIPANT);
        when(documents.saveAndFlush(any(PrintedForm.class))).thenAnswer(saved -> saved.getArgument(0));

        AttachmentResponse response = AttachmentResponses.of(registry.register(AttachmentCommands.of(Uploads.jpeg()), FILE));

        assertThat(response.participantId()).isEqualTo(Uploads.PARTICIPANT);
        verify(documents).saveAndFlush(any(PrintedForm.class));
    }

    private void knownRequest(boolean cancelled, UUID... participants) {
        IssuanceRequest request = IssuanceRequest.start(Uploads.REQUEST, NOW);
        request.takeComposition(Arrays.stream(participants)
                .map(participant -> new RequestParticipant(participant, DocumentKind.LM_POLICY)).toList(), NOW);
        if (cancelled) {
            request.cancel(NOW);
        }
        when(requests.findById(Uploads.REQUEST)).thenReturn(Optional.of(request));
    }

    private static PrintedForm certificate(UUID participant, String hash) {
        return PrintedForms.receivedCertificate(AttachmentOwners.certificate(Uploads.REQUEST, participant, Uploads.ATTACHMENT),
                new StoredFile(FILE.key(), hash, FILE.size(), FILE.contentType()), NOW.minusSeconds(300));
    }
}
