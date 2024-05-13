package HTTPProxy;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyStorage {
    private static ProxyStorage obj_instance;
    private static final Object obj_lock = new Object();

    private final File configDir;

    private final File cacheDir;
    private final File cacheIndex;
    private final ArrayList<String> writeLock;
    private final ConcurrentHashMap<String, Date> cacheMap;

    public static ProxyStorage getBlockedHosts() throws IOException {
        ProxyStorage obj = obj_instance;
        if (obj_instance == null) {
            synchronized (obj_lock) {
                // While a thread was waiting for this lock another thread might have initialized it
                obj = obj_instance;
                if (obj == null) {
                    obj = new ProxyStorage();
                    obj_instance = obj;
                }
            }
        }
        return obj;
    }

    private ProxyStorage() throws IOException {
        String homeDir = System.getProperty("user.home");
        configDir = new File(homeDir + File.separator + ".proxy");
        // Does the config dir exists?
        if (!configDir.mkdirs()) {
            // TODO Load config
        }
        cacheDir = new File(configDir + File.separator + "cache");
        // Does the cache dir exists?
        if (!cacheDir.mkdirs()) {
            // todo load cache
        }
        cacheIndex = new File(cacheDir + File.separator + "index");
        cacheMap = new ConcurrentHashMap<>();
        writeLock = new ArrayList<String>();
    }

    public void saveToCache(String fileName, byte[] data) throws IOException {
        synchronized (writeLock) {
            // Check if another thread is trying to save the same cache
            if(writeLock.contains(fileName)) {
                return;
            }
            else {
                writeLock.add(fileName);
            }
        }
        FileOutputStream cacheFile = new FileOutputStream(cacheDir + File.separator + fileName);
        cacheFile.write(data, 0, data.length);
        cacheFile.flush();
        cacheFile.close();
        cacheMap.put(fileName, new Date());
        synchronized (cacheIndex) {
            try (FileOutputStream fileOut = new FileOutputStream(cacheIndex);
                 ObjectOutputStream objectStream = new ObjectOutputStream(fileOut)) {
                objectStream.writeObject(cacheMap);
            }
        }
        writeLock.remove(fileName);
    }
}
