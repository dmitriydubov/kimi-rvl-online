package ru.projectx.contracts.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.service.storage.AttachmentUploads;

@InternalApi
public class AttachmentController {

    private final AttachmentUploads uploads;

    public AttachmentController(AttachmentUploads uploads) {
        this.uploads = uploads;
    }

    @PostMapping(path = "/requests/{individual_request_id}/participants/{participant_id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse upload(
            @PathVariable("individual_request_id") String individualRequestId,
            @PathVariable("participant_id") String participantId,
            @RequestParam("attachment_id") String attachmentId,
            @RequestPart("file") MultipartFile file) {
        return uploads.receive(UploadRequests.certificate(individualRequestId, participantId, attachmentId, file));
    }
}
