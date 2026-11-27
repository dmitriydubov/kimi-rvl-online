package ru.projectx.contracts.repository.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class PrintedFormRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final UUID PARTICIPANT = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final ParticipantAttachment OWNER = AttachmentOwners.certificate(REQUEST, PARTICIPANT, ATTACHMENT);
    private static final StoredFile FILE = new StoredFile("certificates/0b6c1d2e/" + ATTACHMENT + ".jpg",
            "7851d78af0fb98bbbda8933f49860a009ac7761c21a1136613c8a6704b156251", 76, "image/jpeg");

    @Autowired
    private PrintedFormRepository documents;

    @Autowired
    private IssuanceRequestRepository requests;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void knownRequest() {
        jdbc.sql("truncate table contract_documents, request_participants, issuance_requests cascade").update();
        transaction.executeWithoutResult(status -> {
            IssuanceRequest request = IssuanceRequest.start(REQUEST, NOW);
            request.takeComposition(List.of(new RequestParticipant(PARTICIPANT, DocumentKind.LM_POLICY)), NOW);
            requests.save(request);
        });
    }

    @Test
    void certificate_isFoundByAttachmentWithItsOwnerAndFile() {
        documents.saveAndFlush(PrintedForms.receivedCertificate(OWNER, FILE, NOW));

        assertThat(documents.findByAttachmentAttachmentId(ATTACHMENT)).get().satisfies(found -> {
            assertThat(found.attachment()).contains(OWNER);
            assertThat(found.storedFile()).contains(FILE);
            assertThat(found.docType()).isEqualTo(PrintedFormType.MEDICAL_CERTIFICATE);
            assertThat(found.lmPolicyId()).isEmpty();
            assertThat(found.createdAt()).isEqualTo(NOW);
        });
        assertThat(jdbc.sql("select doc_type::text from contract_documents").query(String.class).single())
                .isEqualTo("MEDICAL_CERTIFICATE");
    }

    @Test
    void unknownAttachment_isNotFound() {
        assertThat(documents.findByAttachmentAttachmentId(ATTACHMENT)).isEmpty();
    }

    @Test
    void secondRecordOfTheSameAttachment_isRefusedByTheDatabase() {
        documents.saveAndFlush(PrintedForms.receivedCertificate(OWNER, FILE, NOW));
        ParticipantAttachment again = AttachmentOwners.certificate(REQUEST, PARTICIPANT, ATTACHMENT);

        assertThatThrownBy(() -> documents.saveAndFlush(PrintedForms.receivedCertificate(again, FILE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void certificateOfAnUnknownRequest_isStoredBeforeTheTask() {
        ParticipantAttachment stranger = AttachmentOwners.certificate(UUID.randomUUID(), PARTICIPANT, UUID.randomUUID());

        documents.saveAndFlush(PrintedForms.receivedCertificate(stranger, FILE, NOW));

        assertThat(documents.findByAttachmentAttachmentId(stranger.attachmentId())).get()
                .satisfies(found -> assertThat(found.attachment()).contains(stranger));
    }

    @Test
    void certificateWithoutFileOrParticipant_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> insert("individual_request_id, participant_id, attachment_id",
                ":request, :participant, :attachment"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_contract_documents_attachment_file");
        assertThatThrownBy(() -> insert("individual_request_id, attachment_id, file_key, file_hash, file_size, content_type",
                ":request, :attachment, 'k', 'h', 1, 'image/png'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_attachment_owner");
    }

    @Test
    void medicalCertificateTypeOutsideAParticipantAttachment_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "individual_request_id, usk_file_url) values (gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, "
                        + "now(), :request, 'https://s3.example.test/x.pdf')").param("request", REQUEST).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, "
                        + "individual_request_id, participant_id, attachment_id, file_key, file_hash, file_size, content_type) "
                        + "values (gen_random_uuid(), 'FINAL', 'POLICY', 1, now(), :request, :participant, :attachment, "
                        + "'k', 'h', 1, 'image/png')")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_contract_documents_attachment_type");
    }

    @Test
    void documentWithoutAnyOwner_isRefusedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, file_key) "
                        + "values (gen_random_uuid(), 'FINAL', 'POLICY', 1, now(), 'k')").update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_contract_documents_single_owner");
    }

    @Test
    void consentWithDeletion_isStoredWithItsFamilyOwner() {
        UUID policy = UUID.randomUUID();
        UUID familyMember = UUID.randomUUID();
        UUID attachment = UUID.randomUUID();

        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, policy_id, "
                        + "family_member_id, attachment_id, file_key, file_hash, file_size, content_type, "
                        + "deleted_at, deleted_request_id) values (gen_random_uuid(), 'FINAL', 'PD_CONSENT', 1, now(), "
                        + ":policy, :family, :attachment, 'k', 'h', 1, 'image/png', now(), 'delete-request-1')")
                .param("policy", policy).param("family", familyMember).param("attachment", attachment).update();

        assertThat(jdbc.sql("select policy_id from contract_documents where attachment_id = :attachment")
                .param("attachment", attachment).query(UUID.class).single()).isEqualTo(policy);
        assertThat(jdbc.sql("select family_member_id from contract_documents where attachment_id = :attachment")
                .param("attachment", attachment).query(UUID.class).single()).isEqualTo(familyMember);
        assertThat(jdbc.sql("select deleted_request_id from contract_documents where attachment_id = :attachment")
                .param("attachment", attachment).query(String.class).single()).isEqualTo("delete-request-1");
    }

    private void insert(String columns, String values) {
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, " + columns + ") values "
                        + "(gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), " + values + ")")
                .param("request", REQUEST).param("participant", PARTICIPANT).param("attachment", ATTACHMENT)
                .update();
    }
}
