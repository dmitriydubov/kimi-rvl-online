package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.Part;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.projectx.contracts.exception.AttachmentFormatException;

class MultipartPartsTest {

    @Test
    void attachmentIdSubmittedAsAFile_isRejectedBeforeTheUpload() throws Exception {
        MultipartHttpServletRequest request = mock();
        Part attachment = mock();
        given(request.getPart("attachment_id")).willReturn(attachment);
        given(attachment.getSubmittedFileName()).willReturn("attachment.txt");
        given(request.getFile("file")).willReturn(new MockMultipartFile("file", "certificate.jpg", "image/jpeg",
                "СТЕНД: вымышленная справка".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> MultipartParts.verify(request))
                .isInstanceOfSatisfying(AttachmentFormatException.class,
                        failure -> assertThat(failure.field()).isEqualTo("attachment_id"));
    }
}
