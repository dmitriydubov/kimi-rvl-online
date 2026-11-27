package ru.projectx.contracts.acceptance;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Когда;
import java.io.IOException;

public class ConsentUploadSteps {

    private final AttachmentCalls calls;

    public ConsentUploadSteps(AttachmentCalls calls) {
        this.calls = calls;
    }

    @Когда("сервис Заявок загружает согласие члена семьи {string} полиса {string}:")
    public void upload(String familyMember, String policy, DataTable table) throws IOException, InterruptedException {
        calls.send(AttachmentUploadSteps.standardConsent(policy, familyMember, table.asMap()));
    }

    @Когда("^сервис Заявок загружает согласие члена семьи \"([^\"]+)\" полиса \"([^\"]+)\" с дефектом \"(.*)\"$")
    public void uploadWithDefect(String familyMember, String policy, String defect)
            throws IOException, InterruptedException {
        AttachmentRequest request = AttachmentUploadSteps.standardConsent(policy, familyMember,
                java.util.Map.of("вложение", "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21", "имя файла", "Тестов_согласие.jpg",
                        "тип", "image/jpeg", "содержимое", "СТЕНД: вымышленное согласие на обработку ПДн, участник 1"));
        calls.send(spoil(request, defect));
    }

    @Когда("сервис Заявок загружает вложение вида {string} участника {string} заявки {string} члена семьи {string} полиса {string}:")
    public void uploadKind(String kind, String participant, String request, String familyMember, String policy,
            DataTable table) throws IOException, InterruptedException {
        AttachmentRequest consent = AttachmentUploadSteps.standardConsent(policy, familyMember, table.asMap());
        switch (kind) {
            case "справка" -> calls.send(AttachmentRequest.certificate(request, participant, consent.attachmentPart(),
                    consent.filePart(), java.util.Optional.empty(), true));
            case "согласие" -> calls.send(consent);
            default -> throw new AssertionError("вида вложения «" + kind + "» нет в перечне шага");
        }
    }

    private static AttachmentRequest spoil(AttachmentRequest request, String defect) {
        String[] path = request.path().split("/");
        if ("идентификатор полиса в пути — не GUID".equals(defect)) {
            return request.withPath("/internal/v1/policies/not-a-guid/family-members/" + path[6] + "/consents");
        }
        if ("идентификатор члена семьи в пути — не GUID".equals(defect)) {
            return request.withPath("/internal/v1/policies/" + path[4] + "/family-members/not-a-guid/consents");
        }
        return AttachmentUploadSteps.spoil(request, defect);
    }
}
