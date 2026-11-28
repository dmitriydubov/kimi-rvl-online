package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;

public class ConsentExclusionSteps {

    private final AttachmentCalls calls;
    private final ReceivedStatusMessages statuses;
    private final ReceivedKmContractSubmissions kmSubmissions;
    private final ReceivedLmPolicyRequests lmRequests;

    public ConsentExclusionSteps(AttachmentCalls calls, ReceivedStatusMessages statuses,
            ReceivedKmContractSubmissions kmSubmissions, ReceivedLmPolicyRequests lmRequests) {
        this.calls = calls;
        this.statuses = statuses;
        this.kmSubmissions = kmSubmissions;
        this.lmRequests = lmRequests;
    }

    @Тогда("ответ о состоянии пакета не содержит согласие по вложению {string}")
    public void packageExcludes(String attachment) {
        assertThat(calls.last().body()).doesNotContain(attachment);
    }

    @Тогда("сообщение о состоянии выпуска №{int} не содержит согласие по вложению {string}")
    public void statusExcludes(int ordinal, String attachment) {
        assertThat(new String(statuses.number(ordinal).getBody(), StandardCharsets.UTF_8)).doesNotContain(attachment);
    }

    @Тогда("передача договора КМ №{int} не содержит согласие по вложению {string}")
    public void kmExcludes(int ordinal, String attachment) {
        assertThat(new String(kmSubmissions.number(ordinal).getBody(), StandardCharsets.UTF_8)).doesNotContain(attachment);
    }

    @Тогда("запрос на выпуск полиса ЛМ №{int} не содержит согласие по вложению {string}")
    public void lmExcludes(int ordinal, String attachment) {
        assertThat(new String(lmRequests.number(ordinal).getBody(), StandardCharsets.UTF_8)).doesNotContain(attachment);
    }
}
