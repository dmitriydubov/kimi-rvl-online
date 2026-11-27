package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.test.web.server.LocalManagementPort;

public class ConsentPrivacySteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final CertificateRecords records;
    private final AttachmentCalls calls;
    private final ServiceJournal journal;
    private final URI metrics;

    public ConsentPrivacySteps(CertificateRecords records, AttachmentCalls calls, ServiceJournal journal,
            @LocalManagementPort int managementPort) {
        this.records = records;
        this.calls = calls;
        this.journal = journal;
        this.metrics = URI.create("http://localhost:" + managementPort + "/actuator/prometheus");
    }

    @Тогда("имя файла {string} и содержимое согласия по вложению {string} не попали ни в БД, ключ S3, журнал, метрики и ответ")
    public void consentDataStaysOut(String filename, String attachment) throws IOException, InterruptedException {
        String content = new String(calls.sentContent(attachment), StandardCharsets.UTF_8);
        String exposition = exposition();
        for (String value : new String[] {filename, content}) {
            assertThat(records.storedRow(attachment)).as("строка реестра").doesNotContain(value);
            assertThat(records.byAttachment(attachment).key()).as("ключ S3").doesNotContain(value);
            assertThat(journal.mentioning(value)).as("журнал").isEmpty();
            assertThat(exposition).as("метрики").doesNotContain(value);
            assertThat(calls.last().body()).as("ответ").doesNotContain(value);
        }
    }

    private String exposition() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(metrics).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
