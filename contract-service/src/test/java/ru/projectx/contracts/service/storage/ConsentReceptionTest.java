package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import ru.projectx.contracts.config.storage.AttachmentProperties;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.document.StoredFile;

class ConsentReceptionTest {

    private static final UUID POLICY = UUID.fromString("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64");
    private static final UUID FAMILY = UUID.fromString("4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6");
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final byte[] CONTENT = "СТЕНД: вымышленное согласие".getBytes(StandardCharsets.UTF_8);
    private static final String HASH = ContentDigests.sha256(() -> new ByteArrayInputStream(CONTENT));
    private static final String KEY = "certificates/0b6c1d2e/" + ATTACHMENT + ".jpg";

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AttachmentMetrics metrics = new AttachmentMetrics(meters);
    private final AttachmentRegistry registry = mock(AttachmentRegistry.class);
    private final ObjectKeys keys = mock(ObjectKeys.class);
    private final CertificateStore store = mock(CertificateStore.class);
    private final AttachmentReception reception = new AttachmentReception(
            new AttachmentFiles(new AttachmentProperties(DataSize.ofMegabytes(5), Map.of("image/jpeg", "jpg")), metrics),
            registry, keys, store, metrics);

    @Test
    void newConsent_isStoredAndAnsweredWithItsFamilyOwner() {
        ConsentUpload upload = upload();
        when(registry.accepted(any(AttachmentCommand.class), eq(HASH))).thenReturn(Optional.empty());
        when(keys.next(ATTACHMENT, "jpg")).thenReturn(KEY);
        when(registry.register(any(AttachmentCommand.class), any(StoredFile.class))).thenAnswer(saved -> {
            StoredFile file = saved.getArgument(1);
            return PrintedForms.receivedAttachment(AttachmentOwners.consent(POLICY, FAMILY, ATTACHMENT),
                    PrintedFormType.PD_CONSENT, file, NOW);
        });

        ConsentResponse response = reception.receive(upload);

        assertThat(response.attachmentId()).isEqualTo(ATTACHMENT);
        assertThat(response.policyId()).isEqualTo(POLICY);
        assertThat(response.familyMemberId()).isEqualTo(FAMILY);
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.fileSize()).isEqualTo(CONTENT.length);
        assertThat(response.fileHash()).isEqualTo(HASH);
        assertThat(response.createdAt()).isEqualTo(NOW);
        verify(store).store(eq(eqStoredFile()), any(ContentSource.class));
        assertThat(meters.get("contracts.attachments.stored").counter().count()).isEqualTo(1);
    }

    @Test
    void repeatedConsent_returnsThePriorRecordWithoutStorage() {
        PrintedForm prior = PrintedForms.receivedAttachment(AttachmentOwners.consent(POLICY, FAMILY, ATTACHMENT),
                PrintedFormType.PD_CONSENT, new StoredFile(KEY, HASH, CONTENT.length, "image/jpeg"), NOW);
        when(registry.accepted(any(AttachmentCommand.class), eq(HASH))).thenReturn(Optional.of(prior));

        ConsentResponse response = reception.receive(upload());

        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.fileHash()).isEqualTo(HASH);
        verifyNoInteractions(store, keys);
        assertThat(meters.find("contracts.attachments.stored").counter()).isNull();
    }

    private StoredFile eqStoredFile() {
        return new StoredFile(KEY, HASH, CONTENT.length, "image/jpeg");
    }

    private static ConsentUpload upload() {
        return new ConsentUpload(POLICY, FAMILY, ATTACHMENT, "image/jpeg", CONTENT.length,
                () -> new ByteArrayInputStream(CONTENT));
    }
}
