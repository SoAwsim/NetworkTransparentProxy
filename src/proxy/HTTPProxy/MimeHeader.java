package proxy.HTTPProxy;

import java.util.HashMap;
import java.util.StringTokenizer;

public class MimeHeader extends HashMap<String, String> {

    public MimeHeader(String s) {
        parse(s);
    }

    private void parse(String data) {
        StringTokenizer st = new StringTokenizer(data, "\r\n");

        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            int colon = s.indexOf(':');
            String key = s.substring(0, colon); // Host: www.google.com
            String value = s.substring(colon + 2);
            put(key, value);
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (String key : keySet()) {
            String val = get(key);
            str.append(key).append(": ").append(val).append("\r\n");
        }
        str.append("\r\n");
        return str.toString();
    }

}
