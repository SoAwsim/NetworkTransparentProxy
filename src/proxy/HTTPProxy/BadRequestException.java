package proxy.HTTPProxy;

import java.io.IOException;

public class BadRequestException extends IOException {
    public BadRequestException(String msg) {
        super(msg);
    }
}
