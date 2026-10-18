package dev.rosewood.rosestacker.hook;

import de.Keyle.MyPet.MyPetApi;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import dev.rosewood.rosestacker.config.SettingKey;
import io.hotmail.com.jacob_vejvoda.infernal_mobs.infernal_mobs;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.bestemor.villagermarket.VillagerMarketAPI;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NPCsHook {

    private static Boolean mythicMobsEnabled;

    private static Boolean citizensEnabled;
    private static Boolean shopkeepersEnabled;
    private static Boolean epicBossesEnabled;
    private static Boolean eliteMobsEnabled;
    private static Boolean bossEnabled;
    private static Boolean proCosmeticsEnabled;
    private static Boolean infernalMobsEnabled;
    private static Boolean simplePetsEnabled;
    private static Boolean levelledMobsEnabled;
    private static Boolean villagerMarketEnabled;
    private static Boolean mypetEnabled;

    private static final NamespacedKey LEVELLEDMOBS_KEY = new NamespacedKey("levelledmobs", "level");

    // Citizens, Shopkeepers, EpicBosses, EliteMobs and SimplePets are no longer available from any
    // public Maven repository, so their APIs are resolved reflectively from the plugins running on
    // the server. Lookups are cached so the hot stacking path never repeats resolution work.

    private static final Map<String, Optional<Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

    /**
     * @return true if Citizens is enabled, false otherwise
     */
    public static boolean citizensEnabled() {
        if (citizensEnabled != null)
            return citizensEnabled;

        return citizensEnabled = Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }

    /**
     * @return true if ShopKeepers is enabled, false otherwise
     */
    public static boolean shopkeepersEnabled() {
        if (shopkeepersEnabled != null)
            return shopkeepersEnabled;

        return shopkeepersEnabled = Bukkit.getPluginManager().isPluginEnabled("Shopkeepers");
    }

    /**
     * @return true if MythicMobs is enabled, false otherwise
     */
    public static boolean mythicMobsEnabled() {
        if (mythicMobsEnabled != null)
            return mythicMobsEnabled;

        return mythicMobsEnabled = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    /**
     * @return true if EpicBosses is enabled, false otherwise
     */
    public static boolean epicBossesEnabled() {
        if (epicBossesEnabled != null)
            return epicBossesEnabled;

        return epicBossesEnabled = Bukkit.getPluginManager().isPluginEnabled("EpicBosses");
    }

    /**
     * @return true if EliteMobs is enabled, false otherwise
     */
    public static boolean eliteMobsEnabled() {
        if (eliteMobsEnabled != null)
            return eliteMobsEnabled;

        return eliteMobsEnabled = Bukkit.getPluginManager().isPluginEnabled("EliteMobs");
    }

    /**
     * @return true if Boss is enabled, false otherwise
     */
    public static boolean bossEnabled() {
        if (bossEnabled != null)
            return bossEnabled;

        return bossEnabled = Bukkit.getPluginManager().isPluginEnabled("Boss");
    }

    /**
     * @return true if ProCosmetics is enabled, false otherwise
     */
    public static boolean proCosmeticsEnabled() {
        if (proCosmeticsEnabled != null)
            return proCosmeticsEnabled;

        return proCosmeticsEnabled = Bukkit.getPluginManager().isPluginEnabled("ProCosmetics");
    }

    /**
     * @return true if InfernalMobs is enabled, false otherwise
     */
    public static boolean infernalMobsEnabled() {
        if (infernalMobsEnabled != null)
            return infernalMobsEnabled;

        return infernalMobsEnabled = Bukkit.getPluginManager().isPluginEnabled("InfernalMobs");
    }

    /**
     * @return true if SimplePets is enabled, false otherwise
     */
    public static boolean simplePetsEnabled() {
        if (simplePetsEnabled != null)
            return simplePetsEnabled;

        boolean enabled = Bukkit.getPluginManager().isPluginEnabled("SimplePets");
        if (!enabled)
            return simplePetsEnabled = false;

        try {
            Class.forName("simplepets.brainsynder.api.plugin.SimplePets");
        } catch (ClassNotFoundException e) {
            return simplePetsEnabled = false;
        }

        return simplePetsEnabled = true;
    }

    /**
     * @return true if LevelledMobs is enabled, false otherwise
     */
    public static boolean levelledMobsEnabled() {
        if (levelledMobsEnabled != null)
            return levelledMobsEnabled;

        return levelledMobsEnabled = Bukkit.getPluginManager().isPluginEnabled("LevelledMobs");
    }

    /**
     * @return true if VillagerMarket is enabled, false otherwise
     */
    public static boolean villagerMarketEnabled() {
        if (villagerMarketEnabled != null)
            return villagerMarketEnabled;

        return villagerMarketEnabled = Bukkit.getPluginManager().isPluginEnabled("VillagerMarket");
    }

    public static boolean mypetEnabled() {
        if (mypetEnabled != null)
            return mypetEnabled;

        boolean enabled = Bukkit.getPluginManager().isPluginEnabled("MyPet");
        if (!enabled)
            return mypetEnabled = false;

        try {
            Class.forName("de.Keyle.MyPet.api.repository.PetManager");
        } catch (ClassNotFoundException e) {
            return mypetEnabled = false;
        }

        return mypetEnabled = true;
    }

    /**
     * @return true if any NPC plugin is enabled, false otherwise
     */
    public static boolean anyEnabled() {
        return citizensEnabled()
                || shopkeepersEnabled()
                || mythicMobsEnabled()
                || epicBossesEnabled()
                || eliteMobsEnabled()
                || bossEnabled()
                || proCosmeticsEnabled()
                || infernalMobsEnabled()
                || simplePetsEnabled()
                || levelledMobsEnabled()
                || villagerMarketEnabled()
                || mypetEnabled();
    }

    /**
     * Checks if a LivingEntity is considered an NPC
     *
     * @param entity The LivingEntity to check
     * @return true if the given LivingEntity is considered an NPC, false otherwise
     */
    public static boolean isNPC(LivingEntity entity) {
        boolean npc = false;

        if (citizensEnabled() && checkCitizens(entity))
            npc = true;

        if (!npc && shopkeepersEnabled() && checkShopkeepers(entity))
            npc = true;

        if (!npc && mythicMobsEnabled() && !SettingKey.MISC_MYTHICMOBS_ALLOW_STACKING.get())
            npc = MythicBukkit.inst().getAPIHelper().isMythicMob(entity);

        if (!npc && epicBossesEnabled() && checkEpicBosses(entity))
            npc = true;

        if (!npc && eliteMobsEnabled() && checkEliteMobs(entity))
            npc = true;

        if (!npc && bossEnabled())
            npc = entity.hasMetadata("Boss_V4");

        if (!npc && proCosmeticsEnabled())
            npc = entity.hasMetadata("PROCOSMETICS_ENTITY");

        if (!npc && infernalMobsEnabled() && !SettingKey.MISC_INFERNALMOBS_ALLOW_STACKING.get()) {
            infernal_mobs plugin = ((infernal_mobs) Bukkit.getPluginManager().getPlugin("InfernalMobs"));
            npc = plugin != null && plugin.idSearch(entity.getUniqueId()) >= 0;
        }

        if (!npc && simplePetsEnabled() && checkSimplePets(entity))
            npc = true;

        if (!npc && levelledMobsEnabled() && !SettingKey.MISC_LEVELLEDMOBS_ALLOW_STACKING.get())
            npc = entity.getPersistentDataContainer().has(LEVELLEDMOBS_KEY, PersistentDataType.INTEGER);

        if (!npc && villagerMarketEnabled())
            npc = VillagerMarketAPI.getShopManager().isShop(entity);

        if (!npc && mypetEnabled() && MyPetApi.isReady())
            npc = MyPetApi.getPetManager().getPetFromEntity(entity) != null;

        return npc;
    }

    public static void addCustomPlaceholders(LivingEntity entity, StringPlaceholders.Builder placeholders) {
        if (levelledMobsEnabled()) {
            Integer level = entity.getPersistentDataContainer().get(LEVELLEDMOBS_KEY, PersistentDataType.INTEGER);
            placeholders.add("levelledmobs_level", level == null ? 0 : level);
        }
    }

    // Reflection-based checks for plugins whose APIs are not hosted on any public Maven repository

    private static boolean checkCitizens(LivingEntity entity) {
        Class<?> api = findClass("Citizens", "net.citizensnpcs.api.CitizensAPI");
        if (api == null || !asBoolean(callStatic(api, "hasImplementation")))
            return false;

        Object registry = callStatic(api, "getNPCRegistry");
        return registry != null && asBoolean(call(registry, "isNPC", entity));
    }

    private static boolean checkShopkeepers(LivingEntity entity) {
        Class<?> api = findClass("Shopkeepers", "com.nisovin.shopkeepers.api.ShopkeepersAPI");
        if (api == null || !asBoolean(callStatic(api, "isEnabled")))
            return false;

        Object registry = callStatic(api, "getShopkeeperRegistry");
        return registry != null && asBoolean(call(registry, "isShopkeeper", entity));
    }

    private static boolean checkEpicBosses(LivingEntity entity) {
        Class<?> epicBosses = findClass("EpicBosses", "com.songoda.epicbosses.EpicBosses");
        if (epicBosses == null)
            return false;

        Object instance = callStatic(epicBosses, "getInstance");
        if (instance == null)
            return false;

        Object bossEntityManager = call(instance, "getBossEntityManager");
        return bossEntityManager != null && call(bossEntityManager, "getActiveBossHolder", entity) != null;
    }

    private static boolean checkEliteMobs(LivingEntity entity) {
        Class<?> entityTracker = findClass("EliteMobs", "com.magmaguy.elitemobs.entitytracker.EntityTracker");
        return entityTracker != null
                && asBoolean(callStatic(entityTracker, "isEliteMob", entity))
                && asBoolean(callStatic(entityTracker, "isNPCEntity", entity));
    }

    private static boolean checkSimplePets(LivingEntity entity) {
        Class<?> simplePets = findClass("SimplePets", "simplepets.brainsynder.api.plugin.SimplePets");
        return simplePets != null && asBoolean(callStatic(simplePets, "isPetEntity", entity));
    }

    /**
     * Loads a class through the classloader of the plugin that owns it, falling back to a regular
     * class lookup. Results are cached; a class that cannot be found is remembered as absent.
     *
     * @param pluginName the plugin providing the class
     * @param className  the fully qualified class name
     * @return the class, or null if it could not be loaded
     */
    private static Class<?> findClass(String pluginName, String className) {
        return CLASS_CACHE.computeIfAbsent(className, key -> {
            try {
                Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
                if (plugin != null)
                    return Optional.of(Class.forName(className, false, plugin.getClass().getClassLoader()));
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Fall through to the generic lookup below
            }

            try {
                return Optional.of(Class.forName(className));
            } catch (ClassNotFoundException | LinkageError e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static Object callStatic(Class<?> clazz, String methodName, Object... args) {
        Method method = findMethod(clazz, methodName, args);
        if (method == null)
            return null;

        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Object call(Object target, String methodName, Object... args) {
        if (target == null)
            return null;

        Method method = findMethod(target.getClass(), methodName, args);
        if (method == null)
            return null;

        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean result && result;
    }

    /**
     * Finds the best matching method for the given name and arguments, preferring an exact parameter
     * match over an assignable one and non-bridge methods over synthetic bridges. Cached per class,
     * method name and argument count.
     */
    private static Method findMethod(Class<?> clazz, String methodName, Object... args) {
        String key = clazz.getName() + '#' + methodName + '/' + args.length;
        return METHOD_CACHE.computeIfAbsent(key, k -> Optional.ofNullable(resolveMethod(clazz, methodName, args)))
                .orElse(null);
    }

    private static Method resolveMethod(Class<?> clazz, String methodName, Object[] args) {
        Method exactNonBridge = null;
        Method assignableNonBridge = null;
        Method bridge = null;

        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(clazz);
        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            if (!visited.add(current))
                continue;

            if (current.getSuperclass() != null)
                queue.add(current.getSuperclass());
            queue.addAll(Arrays.asList(current.getInterfaces()));

            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length)
                    continue;

                Class<?>[] parameters = method.getParameterTypes();
                boolean exact = true;
                boolean assignable = true;
                for (int i = 0; i < parameters.length; i++) {
                    Object arg = args[i];
                    if (arg == null) {
                        if (parameters[i].isPrimitive())
                            assignable = false;
                        exact = false;
                        continue;
                    }

                    Class<?> parameter = box(parameters[i]);
                    if (!parameter.isInstance(arg)) {
                        assignable = false;
                        break;
                    }
                    if (parameter != arg.getClass())
                        exact = false;
                }

                if (!assignable)
                    continue;

                if (method.isBridge()) {
                    if (bridge == null)
                        bridge = method;
                } else if (exact && exactNonBridge == null) {
                    exactNonBridge = method;
                } else if (!exact && assignableNonBridge == null) {
                    assignableNonBridge = method;
                }
            }
        }

        if (exactNonBridge != null)
            return exactNonBridge;
        if (assignableNonBridge != null)
            return assignableNonBridge;
        return bridge;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive())
            return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type;
    }

}
