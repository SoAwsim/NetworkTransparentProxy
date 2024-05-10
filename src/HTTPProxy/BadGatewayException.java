package HTTPProxy;

import java.io.IOException;

public class BadGatewayException extends IOException {
    public BadGatewayException(String msg, Throwable ex) {
        super(msg, ex);
    }
}
