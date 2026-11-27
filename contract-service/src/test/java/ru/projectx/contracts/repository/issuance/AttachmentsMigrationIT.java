package ru.projectx.contracts.repository.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class AttachmentsMigrationIT {

    private static final UUID EARLY = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final UUID CONSENT = UUID.fromString("7e91d4b2-0c38-4f5a-8b17-2d6e9a3f0c54");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyRegistry() {
        jdbc.sql("truncate table contract_documents").update();
    }

    @Test
    void rollback_removesNewRowsAndRestoresTheRequestForeignKeyThenUpdateReappliesTheSchema() throws Exception {
        insertEarly(EARLY, false);
        insertConsent();
        insertEarly(UUID.randomUUID(), true);

        rollbackAttachments();
        assertThat(countByAttachment(EARLY)).isZero();
        assertThat(countByAttachment(CONSENT)).isZero();
        assertThat(countNewColumns()).isZero();
        assertThatThrownBy(() -> insertLegacyEarly(EARLY)).hasMessageContaining("fk_contract_documents_request");

        updateAttachments();
        assertThat(countNewColumns()).isEqualTo(4);
        insertEarly(EARLY, false);
        assertThat(countByAttachment(EARLY)).isEqualTo(1);
    }

    @Test
    void deletion_requiresTheSameNonBlankRequestMarker() {
        insertEarly(EARLY, false);

        assertThatThrownBy(() -> markDeleted(null))
                .hasMessageContaining("ck_contract_documents_deletion");
        assertThatThrownBy(() -> jdbc.sql("update contract_documents set deleted_request_id = '   ' "
                        + "where attachment_id = :attachment").param("attachment", EARLY).update())
                .hasMessageContaining("ck_contract_documents_deletion");

        markDeleted("delete-request-1");
        assertThat(jdbc.sql("select deleted_request_id from contract_documents where attachment_id = :attachment")
                .param("attachment", EARLY).query(String.class).single()).isEqualTo("delete-request-1");
    }

    private void markDeleted(String requestId) {
        if (requestId == null) {
            jdbc.sql("update contract_documents set deleted_at = now() where attachment_id = :attachment")
                    .param("attachment", EARLY).update();
            return;
        }
        jdbc.sql("update contract_documents set deleted_at = now(), deleted_request_id = :request "
                        + "where attachment_id = :attachment")
                .param("request", requestId).param("attachment", EARLY).update();
    }

    private void rollbackAttachments() throws Exception {
        withLiquibase(liquibase -> liquibase.rollback(1, ""));
    }

    private void updateAttachments() throws Exception {
        withLiquibase(Liquibase::update);
    }

    private void withLiquibase(LiquibaseCall call) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                    new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(), database)) {
                call.run(liquibase);
            }
        }
    }

    private void insertEarly(UUID attachment, boolean deleted) {
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, individual_request_id, "
                        + "participant_id, attachment_id, file_key, file_hash, file_size, content_type, deleted_at, "
                        + "deleted_request_id) values (gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), "
                        + ":request, :participant, :attachment, 'k', 'h', 1, 'image/png', "
                        + (deleted ? "now(), 'delete-request-1'" : "null, null") + ")")
                .param("request", UUID.randomUUID()).param("participant", UUID.randomUUID())
                .param("attachment", attachment).update();
    }

    private void insertLegacyEarly(UUID attachment) {
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, individual_request_id, "
                        + "participant_id, attachment_id, file_key, file_hash, file_size, content_type) values "
                        + "(gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), :request, :participant, "
                        + ":attachment, 'k', 'h', 1, 'image/png')")
                .param("request", UUID.randomUUID()).param("participant", UUID.randomUUID())
                .param("attachment", attachment).update();
    }

    private void insertConsent() {
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, policy_id, "
                        + "family_member_id, attachment_id, file_key, file_hash, file_size, content_type) values "
                        + "(gen_random_uuid(), 'FINAL', 'PD_CONSENT', 1, now(), :policy, :family, :attachment, "
                        + "'k', 'h', 1, 'image/png')")
                .param("policy", UUID.randomUUID()).param("family", UUID.randomUUID())
                .param("attachment", CONSENT).update();
    }

    private int countByAttachment(UUID attachment) {
        return jdbc.sql("select count(*) from contract_documents where attachment_id = :attachment")
                .param("attachment", attachment).query(Integer.class).single();
    }

    private int countNewColumns() {
        return jdbc.sql("select count(*) from information_schema.columns where table_name = 'contract_documents' "
                        + "and column_name in ('policy_id', 'family_member_id', 'deleted_at', 'deleted_request_id')")
                .query(Integer.class).single();
    }

    @FunctionalInterface
    private interface LiquibaseCall {
        void run(Liquibase liquibase) throws Exception;
    }
}
