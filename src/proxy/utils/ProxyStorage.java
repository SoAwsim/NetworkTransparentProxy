package proxy.utils;

import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyStorage {
    private static ProxyStorage obj_instance;
    private static final Object obj_lock = new Object();

    private final File cacheDir;
    private final File cacheIndex;
    private final ConcurrentHashMap<String, Object> writeLock;
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
            // Read object back into the memory
            try (FileInputStream index = new FileInputStream(blockedIndex);
                ObjectInputStream blocked = new ObjectInputStream(index)) {
                blockedMap = (ConcurrentHashMap<String, String>) blocked.readObject();
            } catch (ClassNotFoundException e) {
                // Try to delete broken blocked_index
                if (!blockedIndex.delete()) {
                    throw new IOException("Failed to delete the broken blocked_index");
                }
                blockedMap = new ConcurrentHashMap<>();
            }
        } else { // Blocked_index does not exist so create a new one
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

        // Initialize cache index
        cacheIndex = new File(configDir + File.separator + "cache_index");
        if (cacheIndex.exists()) {
            try (FileInputStream index = new FileInputStream(cacheIndex);
                ObjectInputStream map = new ObjectInputStream(index)) {
                cacheMap = (ConcurrentHashMap<String, String>) map.readObject();
            } catch (ClassNotFoundException e) {
                // Try to remove broken
                if (!cacheIndex.delete()) {
                    throw new IOException("Failed to delete the broken cache_index");
                }
                for (File file: Objects.requireNonNull(cacheDir.listFiles())) {
                    if (!file.delete()) {
                        throw new IOException("Failed to remove the broken cache files!");
                    }
                }

                cacheMap = new ConcurrentHashMap<>();
            }
        } else {
            cacheMap = new ConcurrentHashMap<>();
        }

        writeLock = new ConcurrentHashMap<>();
    }

    public boolean isBlocked(InetAddress ip) {
        String hostname = ip.getHostName();
        if (hostname.startsWith("www.")) { // Remove www. part since we do not store that
            hostname = hostname.substring(4);
        }
        return blockedMap.get(hostname) != null;
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

    public Object[] isCached(URL fileName) throws IOException {
        String encodedFileName = URLEncoder.encode(fileName.getHost() + fileName.getPath(), StandardCharsets.UTF_8);
        String cacheDate = cacheMap.get(encodedFileName);
        if (cacheDate != null) {
            return new Object[] {new FileInputStream(cacheDir + File.separator + encodedFileName + ".data"), cacheDate};
        }
        return null;
    }

    public FileOutputStream getCacheInput(URL fileName) throws IOException {
        String encodedFileName = URLEncoder.encode(fileName.getHost() + fileName.getPath(), StandardCharsets.UTF_8);
        Object prev = writeLock.putIfAbsent(encodedFileName, new Object());
        // Another thread has the lock exit
        if (prev != null) {
            return null;
        }
        return new FileOutputStream(cacheDir + File.separator + encodedFileName + ".data");
    }

    public void saveCacheIndex(URL fileName, String date) throws IOException {
        String encodedFileName = URLEncoder.encode(fileName.getHost() + fileName.getPath(), StandardCharsets.UTF_8);
        Object prev = writeLock.get(encodedFileName);
        // File not locked, possible wrong call exit
        if (prev == null) {
            return;
        }
        cacheMap.put(encodedFileName, date);
        // Lock cacheIndex file to prevent multiple threads from writing to it
        synchronized (cacheIndex) {
            try (FileOutputStream fileOut = new FileOutputStream(cacheIndex);
                 ObjectOutputStream objectStream = new ObjectOutputStream(fileOut)) {
                objectStream.writeObject(cacheMap);
            }
        }
        writeLock.remove(encodedFileName);
    }
}
