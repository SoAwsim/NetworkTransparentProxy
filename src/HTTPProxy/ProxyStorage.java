package HTTPProxy;

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyStorage {
    private static ProxyStorage obj_instance;
    private static final Object obj_lock = new Object();

    private final File cacheDir;
    private final File cacheIndex;
    private final ArrayList<String> writeLock;
    private final File blockedIndex;
    private ConcurrentHashMap<String, String> cacheMap;
    private ConcurrentHashMap<String, String> blockedMap;

    public static ProxyStorage getStorage() throws IOException {
        ProxyStorage obj = obj_instance;
        if (obj == null) {
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
        File configDir = new File(homeDir + File.separator + ".proxy");
        // Does the config dir exists?
        if (!configDir.exists()) {
            if (!configDir.mkdirs()) {
                throw new IOException("Config directory creation failed!");
            }
        }

        // Initialize blocked hosts
        blockedIndex = new File(configDir + File.separator + "blocked_index");
        if (blockedIndex.exists()) {
            try (FileInputStream index = new FileInputStream(blockedIndex);
                ObjectInputStream blocked = new ObjectInputStream(index)) {
                blockedMap = (ConcurrentHashMap<String, String>) blocked.readObject();
            } catch (ClassNotFoundException e) {
                blockedIndex.delete();
                blockedMap = new ConcurrentHashMap<>();
            }
        } else {
            blockedMap = new ConcurrentHashMap<>();
        }

        // Initialize cache
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

    public boolean isBlocked(InetAddress ip) {
        return blockedMap.get(ip.getHostName()) != null;
    }

    public void blockAddress(String address) throws IOException {
        InetAddress ip = InetAddress.getByName(address);
        String hostname = ip.getHostName();
        if (hostname.startsWith("www.")) {
            hostname = hostname.substring(4);
        }
        String previous = blockedMap.putIfAbsent(hostname, ip.getHostAddress());
        if (previous == null) {
            try (FileOutputStream fileOut = new FileOutputStream(blockedIndex);
                 ObjectOutputStream objectStream = new ObjectOutputStream(fileOut)) {
                objectStream.writeObject(blockedMap);
            }
        }
    }

    public Set<Map.Entry<String, String>> getAllBlocked() {
        return blockedMap.entrySet();
    }

    public void unblockHosts(InetAddress[] ipArray) throws IOException {
        for (InetAddress ip : ipArray) {
            blockedMap.remove(ip.getHostName());
        }
        try (FileOutputStream fileOut = new FileOutputStream(blockedIndex);
             ObjectOutputStream objectStream = new ObjectOutputStream(fileOut)) {
            objectStream.writeObject(blockedMap);
        }
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
