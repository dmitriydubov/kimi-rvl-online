package ru.projectx.contracts.acceptance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class CertificateRecords {

    private static final String FIELDS = "id, attachment_id, individual_request_id, participant_id, policy_id, "
            + "family_member_id, doc_type::text as doc_type, content_type, file_size, file_hash, file_key, created_at, "
            + "deleted_at, deleted_request_id";
    private static final String CERTIFICATES = "select " + FIELDS + " from contract_documents "
            + "where individual_request_id = :request and participant_id = :participant order by created_at, id";
    private static final String CONSENTS = "select " + FIELDS + " from contract_documents "
            + "where policy_id = :policy and family_member_id = :family order by created_at, id";
    private static final String BY_ATTACHMENT = "select " + FIELDS + " from contract_documents "
            + "where attachment_id = :attachment";
    private static final String REKEY = "update contract_documents set file_key = :key where attachment_id = :attachment";
    private static final String DELETE = "update contract_documents set deleted_at = now(), deleted_request_id = :request "
            + "where attachment_id = :attachment";
    private static final String SEED_CONSENT = "insert into contract_documents (id, kind, doc_type, version, created_at, "
            + "policy_id, family_member_id, attachment_id, file_key, file_hash, file_size, content_type) values "
            + "(gen_random_uuid(), 'FINAL', 'PD_CONSENT', 1, now(), :policy, :family, :attachment, :key, 'h', 1, 'image/jpeg')";

    private final JdbcClient jdbc;

    public CertificateRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Certificate(UUID id, UUID attachment, @Nullable UUID request, @Nullable UUID participant,
            @Nullable UUID policy, @Nullable UUID familyMember, String docType, String contentType, long size,
            String hash, String key, Instant createdAt, @Nullable Instant deletedAt,
            @Nullable String deletedRequestId) {
    }

    List<Certificate> of(UUID request, UUID participant) {
        return jdbc.sql(CERTIFICATES).param("request", request).param("participant", participant)
                .query(this::certificate).list();
    }

    List<Certificate> consents(UUID policy, UUID familyMember) {
        return jdbc.sql(CONSENTS).param("policy", policy).param("family", familyMember)
                .query(this::certificate).list();
    }

    List<Certificate> allByAttachment(String attachment) {
        return jdbc.sql(BY_ATTACHMENT).param("attachment", UUID.fromString(attachment)).query(this::certificate).list();
    }

    Certificate byAttachment(String attachment) {
        return jdbc.sql(BY_ATTACHMENT).param("attachment", UUID.fromString(attachment)).query(this::certificate)
                .optional()
                .orElseThrow(() -> new AssertionError("в реестре документов нет справки по вложению " + attachment));
    }

    void rekey(String attachment, String key) {
        int updated = jdbc.sql(REKEY).param("key", key).param("attachment", UUID.fromString(attachment)).update();
        if (updated != 1) {
            throw new AssertionError("в реестре документов нет справки по вложению " + attachment);
        }
    }

    void markDeleted(String attachment, String requestId) {
        int updated = jdbc.sql(DELETE).param("request", requestId).param("attachment", UUID.fromString(attachment))
                .update();
        if (updated != 1) {
            throw new AssertionError("в реестре документов нет вложения " + attachment);
        }
    }

    void seedConsents(UUID policy, UUID familyMember, int count) {
        for (int index = 0; index < count; index++) {
            UUID attachment = UUID.randomUUID();
            jdbc.sql(SEED_CONSENT).param("policy", policy).param("family", familyMember)
                    .param("attachment", attachment).param("key", "certificates/" + UUID.randomUUID() + "/" + attachment
                            + ".jpg").update();
        }
    }

    String storedRow(String attachment) {
        return jdbc.sql("select row_to_json(contract_documents)::text from contract_documents where attachment_id = :attachment")
                .param("attachment", UUID.fromString(attachment)).query(String.class).single();
    }

    private Certificate certificate(ResultSet row, int index) throws SQLException {
        return new Certificate(row.getObject("id", UUID.class), row.getObject("attachment_id", UUID.class),
                row.getObject("individual_request_id", UUID.class), row.getObject("participant_id", UUID.class),
                row.getObject("policy_id", UUID.class), row.getObject("family_member_id", UUID.class),
                row.getString("doc_type"), row.getString("content_type"), row.getLong("file_size"),
                row.getString("file_hash"), row.getString("file_key"),
                row.getObject("created_at", OffsetDateTime.class).toInstant(), instant(row, "deleted_at"),
                row.getString("deleted_request_id"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
