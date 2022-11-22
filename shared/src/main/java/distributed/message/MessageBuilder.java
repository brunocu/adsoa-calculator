package distributed.message;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

public class MessageBuilder {
    ContentCode contentCode = null;
    Long requestUID = null;
    Long serviceUID = null;
    Long fingerprint = null;
    byte[] body = null;

    public MessageBuilder() {
    }

    public MessageBuilder(ContentCode contentCode, Long requestUID, Long serviceUID, Long fingerprint, byte[] body) {
        this.contentCode = contentCode;
        this.requestUID = requestUID;
        this.serviceUID = serviceUID;
        this.fingerprint = fingerprint;
        if (body != null) {
            this.body = Arrays.copyOf(body, body.length);
        } else {
            this.body = null;
        }
    }

    public static MessageBuilder from(Message from) {
        return new MessageBuilder(
                from.contentCode(),
                from.requestUID(),
                from.serviceUID(),
                from.fingerprint(),
                from.body()
        );
    }

    public Message build() {
        return new Message(contentCode, requestUID, serviceUID, fingerprint, body);
    }

    public MessageBuilder contentCode(ContentCode contentCode) {
        this.contentCode = Objects.requireNonNull(contentCode);
        return this;
    }

    public MessageBuilder requestUID(long requestUID) {
        this.requestUID = requestUID;
        return this;
    }

    public MessageBuilder serviceUID(long serviceUID) {
        this.serviceUID = serviceUID;
        return this;
    }

    public MessageBuilder fingerprint(long fingerprint) {
        this.fingerprint = fingerprint;
        return this;
    }

    public MessageBuilder body(byte[] body) {
        if (body != null) {
            this.body = Arrays.copyOf(body, body.length);
        } else {
            this.body = null;
        }
        return this;
    }

    public MessageBuilder digestFingerprint(int numOp) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            messageDigest.update(contentCode.name().getBytes());
            messageDigest.update(
                    ByteBuffer.allocate(Long.BYTES).putLong(0, requestUID).array()
            );
            if (serviceUID != null) {
                messageDigest.update(
                        ByteBuffer.allocate(Long.BYTES).putLong(0, serviceUID).array()
                );
            }
            if (body != null) {
                messageDigest.update(body);
            }
            messageDigest.update(
                    ByteBuffer.allocate(Integer.BYTES).putInt(0, numOp).array()
            );
            byte[] digestBytes = messageDigest.digest();
            fingerprint = ByteBuffer.wrap(digestBytes).getLong();
        } catch (NoSuchAlgorithmException ignored) {
        }
        return this;
    }
}
