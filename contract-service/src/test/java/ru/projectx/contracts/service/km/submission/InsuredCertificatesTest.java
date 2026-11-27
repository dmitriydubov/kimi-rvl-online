package ru.projectx.contracts.service.km.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class InsuredCertificatesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final StoredFile FILE = new StoredFile("certificates/f/a.jpg", "a".repeat(64), 76, "image/jpeg");

    private final PrintedFormRepository forms = mock(PrintedFormRepository.class);
    private final InsuredCertificates certificates = new InsuredCertificates(forms);
    private final Contract contract = Issuances.contract("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64", Tasks.SPOUSE,
            "DKM-2026-000001");

    @Test
    void of_returnsOnlyActiveMedicalCertificatesOfTheInsured() {
        PrintedForm active = certificate(PrintedFormType.MEDICAL_CERTIFICATE);
        PrintedForm consent = certificate(PrintedFormType.PD_CONSENT);
        PrintedForm deleted = mock(PrintedForm.class);
        when(deleted.deletedAt()).thenReturn(Optional.of(NOW));
        when(deleted.docType()).thenReturn(PrintedFormType.MEDICAL_CERTIFICATE);
        when(forms.findByAttachmentIndividualRequestIdAndAttachmentParticipantIdOrderByCreatedAtAscIdAsc(
                Tasks.REQUEST, Tasks.SPOUSE)).thenReturn(List.of(active, consent, deleted));

        List<SubmittedCertificate> result = certificates.of(contract);

        assertThat(result).singleElement().satisfies(certificate ->
                assertThat(certificate.file()).isEqualTo(FILE));
    }

    private static PrintedForm certificate(PrintedFormType type) {
        return PrintedForms.receivedAttachment(AttachmentOwners.certificate(Tasks.REQUEST, Tasks.SPOUSE,
                UUID.randomUUID()), type, FILE, NOW);
    }
}
