package ru.projectx.contracts.service.km.submission;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.exception.CertificateKeyMissingException;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

@Component
public class InsuredCertificates {

    private final PrintedFormRepository forms;

    public InsuredCertificates(PrintedFormRepository forms) {
        this.forms = forms;
    }

    public List<SubmittedCertificate> of(Contract contract) {
        return forms.findByAttachmentIndividualRequestIdAndAttachmentParticipantIdOrderByCreatedAtAscIdAsc(
                        contract.state().request().individualRequestId(), contract.participantId()).stream()
                .filter(form -> form.deletedAt().isEmpty())
                .filter(form -> form.docType() == PrintedFormType.MEDICAL_CERTIFICATE)
                .map(InsuredCertificates::certificate)
                .toList();
    }

    private static SubmittedCertificate certificate(PrintedForm form) {
        return new SubmittedCertificate(form.id(), form.docType(), form.storedFile()
                .orElseThrow(() -> new CertificateKeyMissingException(form.id())));
    }
}
