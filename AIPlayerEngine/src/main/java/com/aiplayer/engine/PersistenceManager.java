package com.aiplayer.engine;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class PersistenceManager {
    private static final Logger LOGGER = Logger.getLogger(PersistenceManager.class.getName());
    private final Map<String, Object> cache = new HashMap<>();
    private final String saveFile;

    public PersistenceManager(String fileName) { this.saveFile = fileName; load(); }

    public void save(String key, Object value) {
        cache.put(key, value);
        if (value instanceof Serializable) persistToFile();
    }

    public Object load(String key) { return cache.get(key); }

    private void persistToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(saveFile))) {
            oos.writeObject(cache);
        } catch (Exception e) { LOGGER.warning("Save failed: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(saveFile))) {
            Object o = ois.readObject();
            if (o instanceof Map) cache.putAll((Map<String, Object>) o);
        } catch (Exception e) { LOGGER.info("No save file found, starting fresh"); }
    }
}
