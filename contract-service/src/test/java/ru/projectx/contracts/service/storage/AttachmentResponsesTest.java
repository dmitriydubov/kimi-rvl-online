package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.entity.document.UskDocument;

class AttachmentResponsesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");

    @Test
    void certificate_isAnsweredWithItsOwnerAndFile() {
        PrintedForm certificate = PrintedForms.receivedCertificate(
                AttachmentOwners.certificate(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT),
                new StoredFile("certificates/f/a.png", Uploads.CONTENT_HASH, 76, "image/png"), NOW);

        assertThat(AttachmentResponses.of(certificate)).isEqualTo(new AttachmentResponse(certificate.id(),
                Uploads.ATTACHMENT, Uploads.REQUEST, Uploads.PARTICIPANT, "image/png", 76, Uploads.CONTENT_HASH, NOW));
    }

    @Test
    void policyForm_isNoCertificate() {
        PrintedForm form = PrintedForms.issuedFor(PersonOwner.lmPolicy(Uploads.PARTICIPANT),
                new UskDocument(PrintedFormType.POLICY, "https://s3.example.test/lm.pdf", 1L), NOW);

        assertThatThrownBy(() -> AttachmentResponses.of(form)).isInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("не справка участника");
    }

    @Test
    void certificateWithoutFile_isNotAnswered() {
        PrintedForm certificate = mock();
        given(certificate.attachment()).willReturn(Optional.of(
                AttachmentOwners.certificate(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT)));
        given(certificate.storedFile()).willReturn(Optional.empty());

        assertThatThrownBy(() -> AttachmentResponses.of(certificate)).isInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("нет файла");
    }
}
