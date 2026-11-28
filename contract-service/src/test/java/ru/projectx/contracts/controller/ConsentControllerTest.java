package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import ru.projectx.contracts.service.storage.ConsentUpload;
import ru.projectx.contracts.service.storage.ConsentUploads;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class ConsentControllerTest {

    private static final UUID POLICY = UUID.fromString("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64");
    private static final UUID FAMILY = UUID.fromString("4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6");
    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final String PATH = "/internal/v1/policies/{policy}/family-members/{family}/consents";
    private static final byte[] CONTENT = "СТЕНД: вымышленное согласие".getBytes(StandardCharsets.UTF_8);

    private final ConsentUploads uploads = mock(ConsentUploads.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AttachmentMetrics metrics = new AttachmentMetrics(meters);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConsentController(uploads))
            .setControllerAdvice(new RequestFormatAdvice(metrics), new DomainErrorAdvice(), new UnexpectedErrorAdvice())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()))
            .addInterceptors(new MultipartInterceptor())
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void acceptedConsent_answersItsOwnerAndFileInSnakeCase() throws Exception {
        when(uploads.receive(any())).thenReturn(new ConsentResponse(UUID.randomUUID(), ATTACHMENT, POLICY, FAMILY,
                "image/jpeg", CONTENT.length, "ab", Instant.parse("2026-02-10T07:05:00Z")));

        mvc.perform(upload(POLICY.toString(), FAMILY.toString(), ATTACHMENT.toString(), file("name.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document_id").isNotEmpty())
                .andExpect(jsonPath("$.attachment_id").value(ATTACHMENT.toString()))
                .andExpect(jsonPath("$.policy_id").value(POLICY.toString()))
                .andExpect(jsonPath("$.family_member_id").value(FAMILY.toString()))
                .andExpect(jsonPath("$.content_type").value("image/jpeg"))
                .andExpect(jsonPath("$.file_size").value(CONTENT.length))
                .andExpect(jsonPath("$.file_hash").value("ab"))
                .andExpect(jsonPath("$.created_at").value("2026-02-10T07:05:00Z"))
                .andExpect(jsonPath("$.individual_request_id").doesNotExist())
                .andExpect(jsonPath("$.participant_id").doesNotExist());
    }

    @Test
    void upload_passesThePolicyFamilyAndFileToTheService() throws Exception {
        mvc.perform(upload(POLICY.toString(), FAMILY.toString(), ATTACHMENT.toString(), file("name.jpg")));

        ArgumentCaptor<ConsentUpload> received = ArgumentCaptor.forClass(ConsentUpload.class);
        verify(uploads).receive(received.capture());
        ConsentUpload upload = received.getValue();
        assertThat(upload.policyId()).isEqualTo(POLICY);
        assertThat(upload.familyMemberId()).isEqualTo(FAMILY);
        assertThat(upload.attachmentId()).isEqualTo(ATTACHMENT);
        assertThat(upload.contentType()).isEqualTo("image/jpeg");
        assertThat(upload.size()).isEqualTo(CONTENT.length);
        assertThat(readAll(upload)).isEqualTo(CONTENT);
    }

    @ParameterizedTest
    @CsvSource({
        "not-a-guid, 4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6, 3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21, policy_id",
        "0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64, not-a-guid, 3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21, family_member_id",
        "0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64, 4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6, not-a-guid, attachment_id",
        "1-1-1-1-1, 4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6, 3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21, policy_id",
        "0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64, 1-1-1-1-1, 3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21, family_member_id",
        "0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64, 4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6, 1-1-1-1-1, attachment_id",
        "0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64, 4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6, ' 3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21 ', attachment_id"})
    void malformedIdentifier_namesTheExactField(String policy, String family, String attachment, String field)
            throws Exception {
        mvc.perform(upload(policy, family, attachment, file("name.jpg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value(field));
        verifyNoInteractions(uploads);
        assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
    }

    @Test
    void missingFile_namesTheFilePartWithoutRejectedMetric() throws Exception {
        mvc.perform(multipart(PATH, POLICY, FAMILY).param("attachment_id", ATTACHMENT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("file"));
        assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"document.txt", ""})
    void attachmentWithFilename_isRejectedAsAFormatError(String filename) throws Exception {
        MockMultipartFile attachment = new MockMultipartFile("attachment_id", filename, MediaType.TEXT_PLAIN_VALUE,
                ATTACHMENT.toString().getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart(PATH, POLICY, FAMILY).file(attachment).file(file("name.jpg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("attachment_id"))
                .andExpect(jsonPath("$.error.details[0].reason").value("неверный формат"));
        verifyNoInteractions(uploads);
        assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void fileWithoutANonBlankFilename_isRejectedAsAFormatError(String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "image/jpeg", CONTENT);

        mvc.perform(multipart(PATH, POLICY, FAMILY).file(file).param("attachment_id", ATTACHMENT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("file"))
                .andExpect(jsonPath("$.error.details[0].reason").value("неверный формат"));
        verifyNoInteractions(uploads);
        assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
    }

    private static MockMultipartHttpServletRequestBuilder upload(String policy, String family, String attachment,
            MockMultipartFile file) {
        return multipart(PATH, policy, family).file(file).param("attachment_id", attachment);
    }

    private static MockMultipartFile file(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg", CONTENT);
    }

    private static byte[] readAll(ConsentUpload upload) throws IOException {
        try (InputStream stream = upload.content().open()) {
            return stream.readAllBytes();
        }
    }
}
