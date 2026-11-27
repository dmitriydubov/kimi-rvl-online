package ru.projectx.contracts.acceptance;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Когда;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

public class AttachmentUploadSteps {

    private static final String ATTACHMENT = "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21";
    private static final String FILE_NAME = "Тестова_Анна_070у.jpg";
    private static final String CONTENT = "СТЕНД: вымышленная справка 070/у, участник 1";
    private static final String JPEG = "image/jpeg";
    private static final String NOT_A_GUID = "not-a-guid";
    private static final byte[] FILLER = "070u".getBytes(StandardCharsets.US_ASCII);
    private static final int OVER_LIMIT = 5_242_881;

    private static final Map<String, UnaryOperator<AttachmentRequest>> DEFECTS = Map.ofEntries(
            Map.entry("тип файла image/tiff", request -> withFile(request, "image/tiff", content(CONTENT))),
            Map.entry("тип файла image/gif", request -> withFile(request, "image/gif", content(CONTENT))),
            Map.entry("тип файла не указан", request -> withFile(request, null, content(CONTENT))),
            Map.entry("пустой файл", request -> withFile(request, JPEG, new byte[0])),
            Map.entry("файл на байт больше предела", request -> withFile(request, JPEG, filler(OVER_LIMIT))),
            Map.entry("идентификатор заявки в пути — не GUID", request -> request.withPath(
                    "/internal/v1/requests/not-a-guid/participants/" + requestParticipant(request) + "/attachments")),
            Map.entry("идентификатор участника в пути — не GUID", request -> request.withPath(
                    "/internal/v1/requests/" + requestRequest(request) + "/participants/not-a-guid/attachments")),
            Map.entry("нет части attachment_id", AttachmentRequest::withoutAttachment),
            Map.entry("attachment_id — не GUID", request -> request.withAttachment(NOT_A_GUID)),
            Map.entry("нет части file", AttachmentRequest::withoutFile),
            Map.entry("attachment_id с filename", request -> request.withAttachmentFilename("document.txt")),
            Map.entry("attachment_id с filename=\"\"", request -> request.withAttachmentFilename("")),
            Map.entry("file без filename", request -> request.withFile(JPEG, content(CONTENT), Optional.empty())),
            Map.entry("file filename=\"\"", request -> request.withFile(JPEG, content(CONTENT), Optional.of(""))),
            Map.entry("file filename=\"   \"", request -> request.withFile(JPEG, content(CONTENT), Optional.of("   "))),
            Map.entry("тело не multipart", request -> AttachmentRequest.certificate(requestRequest(request),
                    requestParticipant(request), request.attachmentPart(), Optional.empty(), Optional.empty(), false)));

    private static final Map<String, String> INVALID_REQUEST_IDS = Map.of(
            "длиной 65 символов", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0",
            "с символом /", "req/01",
            "пустой", "");

    private final AttachmentCalls calls;

    public AttachmentUploadSteps(AttachmentCalls calls) {
        this.calls = calls;
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string}:")
    public void upload(String participant, String request, DataTable table) throws IOException, InterruptedException {
        calls.send(request(request, participant, table.asMap()));
    }

    @Когда("^сервис Заявок загружает справку участника \"([^\"]+)\" заявки \"([^\"]+)\" с дефектом \"(.*)\"$")
    public void uploadWithDefect(String participant, String request, String defect)
            throws IOException, InterruptedException {
        calls.send(spoil(standard(request, participant), defect));
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string} с заголовком X-Request-Id {string}:")
    public void uploadWithRequestId(String participant, String request, String requestId, DataTable table)
            throws IOException, InterruptedException {
        calls.send(request(request, participant, table.asMap()).withRequestId(requestId));
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string} с недопустимым заголовком X-Request-Id {string}:")
    public void uploadWithInvalidRequestId(String participant, String request, String kind, DataTable table)
            throws IOException, InterruptedException {
        String requestId = Optional.ofNullable(INVALID_REQUEST_IDS.get(kind))
                .orElseThrow(() -> new AssertionError("недопустимого заголовка «" + kind + "» нет в перечне шага"));
        calls.send(request(request, participant, table.asMap()).withRequestId(requestId));
    }

    static AttachmentRequest spoil(AttachmentRequest request, String defect) {
        return Optional.ofNullable(DEFECTS.get(defect))
                .orElseThrow(() -> new AssertionError("дефекта «" + defect + "» нет в перечне шага"))
                .apply(request);
    }

    static AttachmentRequest standardConsent(String policy, String familyMember, Map<String, String> fields) {
        byte[] content = Optional.ofNullable(fields.get("содержимое")).map(AttachmentUploadSteps::content)
                .orElseGet(() -> filler(Integer.parseInt(required(fields, "размер"))));
        return AttachmentRequest.consent(UploadFixtures.policy(policy), UploadFixtures.familyMember(familyMember),
                Optional.of(UploadFixtures.attachment(fields)),
                Optional.of(new AttachmentRequest.FilePart(Optional.of(UploadFixtures.filename(fields)),
                        required(fields, "тип"), content)));
    }

    static String invalidRequestId(String kind) {
        return Optional.ofNullable(INVALID_REQUEST_IDS.get(kind))
                .orElseThrow(() -> new AssertionError("недопустимого заголовка «" + kind + "» нет в перечне шага"));
    }

    private static AttachmentRequest request(String request, String participant, Map<String, String> fields) {
        byte[] content = Optional.ofNullable(fields.get("содержимое")).map(AttachmentUploadSteps::content)
                .orElseGet(() -> filler(Integer.parseInt(required(fields, "размер"))));
        return AttachmentRequest.certificate(request, UploadFixtures.participant(participant),
                Optional.of(UploadFixtures.attachment(fields)),
                Optional.of(new AttachmentRequest.FilePart(Optional.of(UploadFixtures.filename(fields)),
                        required(fields, "тип"), content)), Optional.empty(), true);
    }

    private static AttachmentRequest standard(String request, String participant) {
        return AttachmentRequest.certificate(request, UploadFixtures.participant(participant), Optional.of(ATTACHMENT),
                Optional.of(new AttachmentRequest.FilePart(Optional.of(FILE_NAME), JPEG, content(CONTENT))),
                Optional.empty(), true);
    }

    private static AttachmentRequest withFile(AttachmentRequest request, @Nullable String contentType,
            byte[] content) {
        return request.withFile(contentType, content, Optional.of(FILE_NAME));
    }

    private static String required(Map<String, String> fields, String name) {
        return Optional.ofNullable(fields.get(name))
                .orElseThrow(() -> new AssertionError("в таблице загрузки нет строки «" + name + "»"));
    }

    private static String requestRequest(AttachmentRequest request) {
        return request.path().split("/")[4];
    }

    private static String requestParticipant(AttachmentRequest request) {
        return request.path().split("/")[6];
    }

    private static byte[] content(String text) {
        return UploadFixtures.content(text).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] filler(int size) {
        byte[] content = new byte[size];
        for (int index = 0; index < size; index++) {
            content[index] = FILLER[index % FILLER.length];
        }
        return content;
    }
}
