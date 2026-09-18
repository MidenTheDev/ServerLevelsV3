package us.to.midensthings.serverLevels.data;

import us.to.midensthings.serverLevels.systems.LevelSystem;

import java.util.*;

public class LevelSystemRegistry {
    private Map<String, LevelSystem> systemMap = new HashMap<>();

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
        systemMap = new HashMap<>();
    }
}
