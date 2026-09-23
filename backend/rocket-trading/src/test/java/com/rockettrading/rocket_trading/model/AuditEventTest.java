import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import com.rockettrading.rocket_trading.model.AuditEvent;
import java.time.Instant;

public class AuditServiceTests {

    @Test
    @DisplayName("constructor initializes fields correctly")
    void constructorInitializesFieldsCorrectly() {
        String eventType = "CREATE";
        Instant occurredAt = Instant.now();
        String recordActor = "user123";

        AuditEvent auditEvent = new AuditEvent(eventType, occurredAt, recordActor);

        assertAll(
                () -> assertEquals(eventType, auditEvent.getEventType()),
                () -> assertEquals(occurredAt, auditEvent.getOccurredAt()),
                () -> assertEquals(recordActor, auditEvent.getRecordActor())
        );
    }

    @Test
    @DisplayName("setter updates fields correctly")
    void setterUpdatesFieldsCorrectly() {
        String eventType = "CREATE";
        Instant occurredAt = Instant.now();
        String recordActor = "user123";

        AuditEvent auditEvent = new AuditEvent(eventType, occurredAt, recordActor);

        String newEventType = "UPDATE";
        Instant newOccurredAt = Instant.now();
        String newRecordActor = "user456";

        auditEvent.setEventType(newEventType);
        auditEvent.setOccurredAt(newOccurredAt);
        auditEvent.setRecordActor(newRecordActor);

        assertAll(
                () -> assertEquals(newEventType, auditEvent.getEventType()),
                () -> assertEquals(newOccurredAt, auditEvent.getOccurredAt()),
                () -> assertEquals(newRecordActor, auditEvent.getRecordActor())
        );
    }
}
