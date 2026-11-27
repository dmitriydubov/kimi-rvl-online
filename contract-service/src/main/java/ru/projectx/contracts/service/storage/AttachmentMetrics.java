package ru.projectx.contracts.service.storage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.AttachmentDefect;

@Component
public class AttachmentMetrics {

    static final String STORED = "contracts.attachments.stored";
    static final String REJECTED = "contracts.attachments.rejected";
    static final String STORAGE_UNAVAILABLE = "contracts.storage.unavailable";
    static final String REASON = "reason";

    private final MeterRegistry registry;

    public AttachmentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countStored() {
        Counter.builder(STORED)
                .description("справки 070/у и согласия, впервые записанные в реестр документов")
                .register(registry)
                .increment();
    }

    public void countRejected(AttachmentDefect defect) {
        Counter.builder(REJECTED)
                .description("файлы справок и согласий, отклонённые по типу или размеру")
                .tag(REASON, defect.name().toLowerCase(Locale.ROOT))
                .register(registry)
                .increment();
    }

    public void countStorageUnavailable() {
        Counter.builder(STORAGE_UNAVAILABLE)
                .description("обращения к хранилищу S3 за вложениями, не получившие успешного ответа")
                .register(registry)
                .increment();
    }
}
