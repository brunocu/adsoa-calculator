package distributed.message;

import java.io.Serializable;

public class Message implements Serializable {
    private final ContentCode contentCode;
    private final String body;

    public Message(ContentCode contentCode, String body) {
        this.contentCode = contentCode;
        this.body = body;
    }

    public ContentCode getContentCode() {
        return contentCode;
    }

    public String getBody() {
        return body;
    }
}
