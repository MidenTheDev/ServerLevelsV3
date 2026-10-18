package us.to.midensthings.serverLevels.data;

import us.to.midensthings.serverLevels.systems.LevelSystem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class LevelSystemRegistry {
    // System names are used directly in SQL table names, so only allow safe identifier characters
    private static final Pattern VALID_SYSTEM_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private final Map<String, LevelSystem> systemMap = new ConcurrentHashMap<>();

    public static boolean isValidSystemName(String systemName) {
        return VALID_SYSTEM_NAME.matcher(systemName).matches();
    }

    public void registerLevelSystem(String systemName) {
        systemMap.put(systemName, new LevelSystem(systemName));
    }

    public LevelSystem getLevelSystem(String systemName) {
        return systemMap.get(systemName);
    }

    public Collection<LevelSystem> getAllSystems() {
        return systemMap.values();
    }

    public void clearRegistry() {
        systemMap.clear();
    }
}
