package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class AttachmentLifecycleSteps {

    private static final Map<String, Function<CertificateRecords.Certificate, UUID>> IDENTIFIERS = Map.of(
            "заявка", CertificateRecords.Certificate::request,
            "участник", CertificateRecords.Certificate::participant,
            "policy_id", CertificateRecords.Certificate::policy,
            "family_member_id", CertificateRecords.Certificate::familyMember);
    private static final Map<String, Function<CertificateRecords.Certificate, String>> TEXT = Map.of(
            "doc_type", CertificateRecords.Certificate::docType,
            "file_hash", CertificateRecords.Certificate::hash);

    private final CertificateRecords records;

    public AttachmentLifecycleSteps(CertificateRecords records) {
        this.records = records;
    }

    @Тогда("в реестре документов по вложению {string} одна запись:")
    public void oneRecord(String attachment, DataTable expected) {
        List<CertificateRecords.Certificate> found = records.allByAttachment(attachment);
        assertThat(found).as("записи вложения %s", attachment).hasSize(1);
        expected.asMaps().forEach(row -> row.forEach((column, value) -> matches(found.getFirst(), column, value)));
    }

    @Тогда("в реестре документов по вложению {string} одна запись вида {string}")
    public void oneRecordOfType(String attachment, String type) {
        List<CertificateRecords.Certificate> found = records.allByAttachment(attachment);
        assertThat(found).as("записи вложения %s", attachment).hasSize(1);
        assertThat(found.getFirst().docType()).isEqualTo(type);
    }

    @Дано("запись справки по вложению {string} помечена удалённой по запросу {string}")
    @Дано("запись согласия по вложению {string} помечена удалённой по запросу {string}")
    public void markDeleted(String attachment, String requestId) {
        records.markDeleted(attachment, UploadFixtures.deleteRequest(requestId));
    }

    @Тогда("запись справки по вложению {string} остаётся удалённой по запросу {string}")
    @Тогда("запись согласия по вложению {string} остаётся удалённой по запросу {string}")
    public void remainsDeleted(String attachment, String requestId) {
        CertificateRecords.Certificate record = records.byAttachment(attachment);
        assertThat(record.deletedAt()).as("deleted_at").isNotNull();
        assertThat(record.deletedRequestId()).as("deleted_request_id").isEqualTo(requestId);
    }

    @Тогда("запись справки по вложению {string} остаётся активной")
    public void remainsActive(String attachment) {
        assertThat(records.byAttachment(attachment).deletedAt()).as("deleted_at").isNull();
    }

    @Тогда("согласия члена семьи {string} полиса {string} в реестре документов:")
    public void consents(String familyMember, String policy, DataTable expected) {
        List<CertificateRecords.Certificate> stored = records.consents(UUID.fromString(policy), UUID.fromString(familyMember));
        List<Map<String, String>> rows = expected.asMaps();
        assertThat(stored).as("согласия члена семьи %s", familyMember).hasSize(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            CertificateRegistrySteps.matches(stored.get(index), rows.get(index));
        }
    }

    @Тогда("в реестре документов нет согласий члена семьи {string} полиса {string}")
    public void noConsents(String familyMember, String policy) {
        assertThat(records.consents(UUID.fromString(policy), UUID.fromString(familyMember))).isEmpty();
    }

    @Дано("у члена семьи {string} полиса {string} уже {int} согласий")
    public void existingConsents(String familyMember, String policy, int count) {
        records.seedConsents(UUID.fromString(policy), UUID.fromString(familyMember), count);
    }

    @Тогда("у члена семьи {string} полиса {string} в реестре документов согласий — {int}")
    public void consentCount(String familyMember, String policy, int count) {
        assertThat(records.consents(UUID.fromString(policy), UUID.fromString(familyMember))).hasSize(count);
    }

    private static void matches(CertificateRecords.Certificate record, String column, String expected) {
        Function<CertificateRecords.Certificate, UUID> identifier = IDENTIFIERS.get(column);
        if (identifier != null) {
            assertThat(identifier.apply(record)).isEqualTo(UUID.fromString(expected));
            return;
        }
        Function<CertificateRecords.Certificate, String> text = java.util.Optional.ofNullable(TEXT.get(column))
                .orElseThrow(() -> new AssertionError("столбца «" + column + "» нет в словаре шага"));
        assertThat(text.apply(record)).isEqualTo(expected);
    }
}
