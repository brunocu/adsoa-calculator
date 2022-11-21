package distributed.message;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public record Message(
        ContentCode contentCode,
        Long requestUID,
        Long serviceUID,
        Long fingerprint,
        byte[] body
) implements Serializable {
    public Message {
        Objects.requireNonNull(contentCode);

        if (body != null) {
            body = Arrays.copyOf(body, body.length);
        }
    }
}
