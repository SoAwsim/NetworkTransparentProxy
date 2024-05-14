package HTTPProxy;

import java.io.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyStorage {
    private static ProxyStorage obj_instance;
    private static final Object obj_lock = new Object();

    private final File configDir;

    private final File cacheDir;
    private final File cacheIndex;
    private final ArrayList<String> writeLock;
    private ConcurrentHashMap<String, String> cacheMap;

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
        if (!configDir.exists()) {
            if (!configDir.mkdirs()) {
                throw new IOException("Config directory creation failed!");
            }
        }

        cacheDir = new File(configDir + File.separator + "cache");
        // Does the cache dir exists?
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                throw new IOException("Cache directory creation failed!");
            }
        } // No need to load files here to the memory

        cacheIndex = new File(configDir + File.separator + "cache_index");
        if (cacheIndex.exists()) {
            try (FileInputStream index = new FileInputStream(cacheIndex);
                ObjectInputStream map = new ObjectInputStream(index)) {
                cacheMap = (ConcurrentHashMap<String, String>) map.readObject();
            } catch (ClassNotFoundException e) {
                // File reading failed for index therefore clear all cache and reset index
                cacheIndex.delete();
                for (File file: Objects.requireNonNull(cacheDir.listFiles())) {
                    file.delete();
                }

                cacheMap = new ConcurrentHashMap<>();
            }
        } else {
            cacheMap = new ConcurrentHashMap<>();
        }

        writeLock = new ArrayList<>();
    }

    public Object[] isCached(String fileName) throws IOException {
        fileName = fileName.replace("/", " ");
        String cacheDate = cacheMap.get(fileName);
        if (cacheDate != null) {
            return new Object[] {new FileInputStream(cacheDir + File.separator + fileName + ".data"), cacheDate};
        }
        return null;
    }

    public FileOutputStream getCacheInput(String fileName) throws IOException {
        // Replace / with empty space / causes problems with the directory structure
        fileName = fileName.replace("/", " ");
        synchronized (writeLock) {
            // Check if another thread is trying to save the same cache
            if(writeLock.contains(fileName)) {
                return null;
            } else {
                writeLock.add(fileName);
            }
        }
        return new FileOutputStream(cacheDir + File.separator + fileName + ".data");
    }

    public void saveCacheIndex(String fileName, String date) throws IOException {
        fileName = fileName.replace("/", " ");
        synchronized (writeLock) {
            if (!writeLock.contains(fileName)) {
                return;
            }
        }
        System.out.println("Saving index");
        cacheMap.put(fileName, date);
        synchronized (cacheIndex) {
            try (FileOutputStream fileOut = new FileOutputStream(cacheIndex);
                 ObjectOutputStream objectStream = new ObjectOutputStream(fileOut)) {
                objectStream.writeObject(cacheMap);
            }
        }
        synchronized (writeLock) {
            writeLock.remove(fileName);
        }
    }
}
