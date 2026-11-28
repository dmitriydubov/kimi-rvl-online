package ru.projectx.contracts.entity.document;

import java.time.Instant;
import ru.projectx.contracts.entity.party.PersonOwner;

public final class PrintedForms {

    private PrintedForms() {
    }

    public static PrintedForm issuedFor(PersonOwner owner, UskDocument document, Instant now) {
        return new PrintedForm(owner, document, now);
    }

    public static PrintedForm receivedCertificate(ParticipantAttachment owner, StoredFile file, Instant now) {
        return receivedAttachment(owner, PrintedFormType.MEDICAL_CERTIFICATE, file, now);
    }

    public static PrintedForm receivedAttachment(ParticipantAttachment owner, PrintedFormType type, StoredFile file,
            Instant now) {
        return new PrintedForm(owner, type, file, now);
    }
}
