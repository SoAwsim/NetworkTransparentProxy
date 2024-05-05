package HTTPProxy;

import java.util.*;

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
        String str = "";
        Iterator<String> e = keySet().iterator();
        while (e.hasNext()) {
            String key = e.next();
            String val = get(key);
            str += key + ": " + val + "\r\n";
        }
        str += "\r\n";
        return str;
    }

}
