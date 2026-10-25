package com.osiris.autoplug.client.worlds;

import com.osiris.autoplug.client.browser.VanillaServersReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Retains only display/version metadata from the shared bounded NBT decoder. */
final class LevelDatReader {
    static final class Metadata {
        String name = "", version = "";
        boolean modded, cheats, hardcore;
        long lastPlayed = -1;
        List<String> dataPacks = new ArrayList<>();
    }

    Metadata read(Path path) throws IOException {
        Map<String, Object> root = new VanillaServersReader().readCompound(path);
        if (!(root.get("Data") instanceof Map<?, ?>)) throw new IOException("level.dat has no Data compound");
        Map<?, ?> data = (Map<?, ?>) root.get("Data");
        Metadata result = new Metadata();
        if (data.containsKey("LevelName") && !(data.get("LevelName") instanceof String)) throw new IOException("Invalid LevelName tag");
        if (data.containsKey("Version") && !(data.get("Version") instanceof Map<?, ?>)) throw new IOException("Invalid Version tag");
        if (data.containsKey("WasModded") && !(data.get("WasModded") instanceof Number)) throw new IOException("Invalid WasModded tag");
        if (data.get("LevelName") instanceof String) result.name = (String) data.get("LevelName");
        if (data.get("Version") instanceof Map<?, ?>) {
            Object name = ((Map<?, ?>) data.get("Version")).get("Name");
            if (name != null && !(name instanceof String)) throw new IOException("Invalid version Name tag");
            if (name instanceof String) result.version = (String) name;
        }
        result.modded = data.get("WasModded") instanceof Number && ((Number) data.get("WasModded")).intValue() != 0;
        result.cheats = numericFlag(data, "allowCommands");
        result.hardcore = numericFlag(data, "hardcore");
        Object lastPlayed = data.get("LastPlayed");
        if (lastPlayed != null && !(lastPlayed instanceof Number)) throw new IOException("Invalid LastPlayed tag");
        if (lastPlayed instanceof Number) result.lastPlayed = Math.max(-1, ((Number) lastPlayed).longValue());
        Object packs = data.get("DataPacks");
        if (packs != null && !(packs instanceof Map<?, ?>)) throw new IOException("Invalid DataPacks tag");
        if (packs instanceof Map<?, ?>) {
            Object enabled = ((Map<?, ?>) packs).get("Enabled");
            if (enabled != null && !(enabled instanceof List<?>)) throw new IOException("Invalid enabled datapacks tag");
            if (enabled instanceof List<?>) for (Object pack : (List<?>) enabled) {
                if (!(pack instanceof String)) throw new IOException("Invalid datapack name");
                if (result.dataPacks.size() == 256) break;
                String name = (String) pack;
                result.dataPacks.add(name.length() > 256 ? name.substring(0, 256) + "…" : name);
            }
        }
        for (String marker : Arrays.asList("FML", "fml", "ForgeData", "forge", "neoforge", "fabric", "Mods", "mods", "ModList"))
            if (root.containsKey(marker) || data.containsKey(marker)) result.modded = true;
        Object brands = data.get("ServerBrands");
        if (brands instanceof List<?>) for (Object brand : (List<?>) brands)
            if (brand instanceof String && !"vanilla".equals(brand)) result.modded = true;
        return result;
    }
    private static boolean numericFlag(Map<?, ?> data, String key) throws IOException {
        Object value = data.get(key);
        if (value != null && !(value instanceof Number)) throw new IOException("Invalid " + key + " tag");
        return value instanceof Number && ((Number) value).intValue() != 0;
    }
}
