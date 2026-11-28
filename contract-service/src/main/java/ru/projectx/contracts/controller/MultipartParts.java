package ru.projectx.contracts.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ru.projectx.contracts.exception.AttachmentFormatException;

final class MultipartParts {

    private static final String ATTACHMENT_ID = "attachment_id";
    private static final String FILE = "file";

    private MultipartParts() {
    }

    static void verify(HttpServletRequest request) throws IOException, ServletException {
        verifyAttachment(request.getPart(ATTACHMENT_ID));
        verifyFilename(requiredFile(request));
    }

    private static MultipartFile requiredFile(HttpServletRequest request) throws MissingServletRequestPartException {
        if (!(request instanceof MultipartHttpServletRequest multipart)) {
            throw new MultipartException("тело запроса не multipart");
        }
        MultipartFile file = multipart.getFile(FILE);
        if (file == null) {
            throw new MissingServletRequestPartException(FILE);
        }
        return file;
    }

    private static void verifyFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new AttachmentFormatException(FILE);
        }
    }

    private static void verifyAttachment(Part attachment) {
        if (attachment != null && attachment.getSubmittedFileName() != null) {
            throw new AttachmentFormatException(ATTACHMENT_ID);
        }
    }
}
