package us.to.midensthings.serverLevels.compat;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.event.AbilityEndEvent;
import com.projectkorra.projectkorra.event.AbilityProgressEvent;
import com.projectkorra.projectkorra.event.AbilityStartEvent;
import com.projectkorra.projectkorra.event.PlayerCooldownChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import us.to.midensthings.serverLevels.ServerLevels;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiConsumer;

/***
 * Detects each use of a ProjectKorra ability exactly once, for ProjectKorra core and addons.
 * <p>
 * ProjectKorra has no "ability used" event. Its start, progress and end events fire per ability instance, and one use can
 * create several instances (combos, child abilities, abilities that spawn others), so this combines two signals:
 * <ul>
 * <li>Abilities with a cooldown count when the cooldown is added. Adding a cooldown that's already running is part of the
 * same use (ProjectKorra won't start an ability while it's on cooldown), so it's ignored. The use is credited to the outermost
 * ability whose code added the cooldown, so cooldowns with other names (e.g. ElementSphere's "ESAir") and child abilities
 * count for the ability the player actually used. Cooldowns an ability puts on other players are ignored.</li>
 * <li>Abilities with no cooldown count when they start, one tick later once ProjectKorra has applied attribute changes
 * (AvatarState sets many cooldowns to 0 only after the start event). Only instances started by the player, not ones
 * spawned by another ability, count.</li>
 * </ul>
 * A few abilities don't fit either rule, so they have a built-in override, verified against each ability's source.
 */
public class PKAbilityUseTracker implements Listener {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    private enum Trigger {
        // Cooldown when the ability has one, start when it doesn't
        AUTO,
        // Only when its cooldown is added
        COOLDOWN,
        // Only when it starts, even if it has a cooldown or was started by another ability
        START,
        // When an instance the player started ends, optionally only if a boolean field shows it was really used
        // (e.g. a charge that was released, not cancelled)
        END,
        // Never counts as a use
        NONE
    }

    /***
     * How one ability class is detected
     * @param trigger when it counts
     * @param creditAs the ability name to credit the use to, or null for its own name
     * @param usedField for END, boolean fields on the ability (separated by |) where any being true means it was really used,
     *                  or null to count every end
     */
    private record Override(Trigger trigger, String creditAs, String usedField) {
        static Override trigger(Trigger trigger) {
            return new Override(trigger, null, null);
        }
    }

    // Used as an END field name: counts if the ability started another ability (e.g. a burst launching its blasts)
    private static final String SPAWNED_CHILD = "@spawnedChild";

    // Ability classes that don't fit the normal rules, by full class name (several classes can share one ability name).
    // Each was checked against the ability's source, see the comment on each entry for why
    private static final Map<String, Override> OVERRIDES = Map.ofEntries(
            // ProjectKorra core
            // Charged on sneak with no cooldown by default, so count only a real burst, not a tap of sneak or a short fall.
            // Every kind of burst (sphere, cone, fall) launches AirBlasts
            Map.entry("com.projectkorra.projectkorra.airbending.AirBurst", new Override(Trigger.END, null, SPAWNED_CHILD)),
            Map.entry("com.projectkorra.projectkorra.firebending.FireBurst", new Override(Trigger.END, null, "charged")),
            // Its cooldown is added after it's removed, which can be by another player's ability, and a normal FireBlast's
            // cooldown can hide it. Count it once it's launched, not when a charge is cancelled
            Map.entry("com.projectkorra.projectkorra.firebending.FireBlastCharged", new Override(Trigger.END, null, "launched")),
            // Adds its cooldown before checking it can be cast, so a combo that fizzles in the air would count
            Map.entry("com.projectkorra.projectkorra.firebending.combo.FireWheel", Override.trigger(Trigger.START)),
            // Adds its cooldown when it ends, which another ability's FireJet cooldown (e.g. JedCore's FireSki) can hide
            Map.entry("com.projectkorra.projectkorra.firebending.FireJet", Override.trigger(Trigger.START)),
            // getCooldown() is always 0 but it adds a cooldown when used, which would count it twice
            Map.entry("com.projectkorra.projectkorra.firebending.FireManipulation", Override.trigger(Trigger.COOLDOWN)),
            // Adds its cooldown at the start and again at the end after the first one ran out
            Map.entry("com.projectkorra.projectkorra.firebending.FireShield", Override.trigger(Trigger.START)),
            // Started automatically when moving into dark areas, not by the player
            Map.entry("com.projectkorra.projectkorra.firebending.Illumination", Override.trigger(Trigger.NONE)),
            // EarthDome cast on someone else. It adds EarthDome's cooldown, so count it as an EarthDome use, once
            Map.entry("com.projectkorra.projectkorra.earthbending.combo.EarthDomeOthers", new Override(Trigger.COOLDOWN, "EarthDome", null)),
            // The hidden dome that both EarthDome combos build. It adds EarthDome's cooldown under its own name "EarthDomeHidden"
            Map.entry("com.projectkorra.projectkorra.earthbending.EarthDome", new Override(Trigger.COOLDOWN, "EarthDome", null)),
            // These start when a water source is selected, so only count them once they actually formed or launched
            Map.entry("com.projectkorra.projectkorra.waterbending.SurgeWall", new Override(Trigger.END, null, "forming")),
            Map.entry("com.projectkorra.projectkorra.waterbending.Torrent", new Override(Trigger.END, null, "forming|formed|launching")),
            Map.entry("com.projectkorra.projectkorra.waterbending.OctopusForm", new Override(Trigger.END, null, "forming|formed")),
            // Count the heal, not every tap of sneak (it has no cooldown and starts even with no water)
            Map.entry("com.projectkorra.projectkorra.waterbending.healing.HealingWaters", new Override(Trigger.END, null, "healing")),
            // Starts on click, but its instance lives on across several freezes and melts
            Map.entry("com.projectkorra.projectkorra.waterbending.ice.PhaseChange", Override.trigger(Trigger.COOLDOWN)),
            // Its cooldown is skipped when the hold ends early, so count the start
            Map.entry("com.projectkorra.projectkorra.waterbending.blood.Bloodbending", Override.trigger(Trigger.START)),
            // Spawned by many water abilities as a side effect
            Map.entry("com.projectkorra.projectkorra.waterbending.plant.PlantRegrowth", Override.trigger(Trigger.NONE)),

            // JedCore
            // Adds FireJet's cooldown to stop FireJet during the ski, count the ski itself
            Map.entry("com.jedk1.jedcore.ability.firebending.FireSki", Override.trigger(Trigger.START)),
            // Started as a child of ElementSphere, and has no cooldown for BlueFire benders
            // Credited as ElementSphere like the other sub-casts
            Map.entry("com.jedk1.jedcore.ability.avatar.elementsphere.ESFire", new Override(Trigger.START, "ElementSphere", null)),
            // getCooldown() returns 3000, but no cooldown is added by default
            Map.entry("com.jedk1.jedcore.ability.earthbending.EarthSurf", Override.trigger(Trigger.START)),
            // getCooldown() is always 0, and it adds cooldowns before and after
            Map.entry("com.jedk1.jedcore.ability.earthbending.MagnetShield", Override.trigger(Trigger.START)),
            // Started on every click while EarthArmor is active
            Map.entry("com.jedk1.jedcore.ability.earthbending.MetalArmor", Override.trigger(Trigger.NONE)),
            // Parts of a MetalCables session, which counts once on its own
            Map.entry("com.jedk1.jedcore.ability.earthbending.metalcables.MetalCablesGrab", Override.trigger(Trigger.NONE)),
            Map.entry("com.jedk1.jedcore.ability.earthbending.metalcables.MetalCablesSlam", Override.trigger(Trigger.NONE)),

            // AvatachTweaks
            // Counts at the start, and adds a stamina based cooldown when the spout ends
            Map.entry("me.dreig_michihi.avatachtweaks.bending.air.AirSpout", Override.trigger(Trigger.START)),
            // Starts when the combo is detected with no cooldown. Only the ability it releases counts
            Map.entry("me.dreig_michihi.avatachtweaks.bending.water.combo.Rainbending", Override.trigger(Trigger.COOLDOWN)),
            // TerraRaise: in AvatarState, selecting a source starts it with no cooldown, raising adds a cooldown
            Map.entry("TerraRaise", Override.trigger(Trigger.COOLDOWN)),
            // SpiritualProjection: only binds the multi-ability, its parts count on their own
            Map.entry("com.loony.spiritualprojection.multiability.SpiritualProjection", Override.trigger(Trigger.NONE)),
            // Leaves the multi-ability, not a use
            Map.entry("com.loony.spiritualprojection.multiability.Exit", Override.trigger(Trigger.NONE))
    );

    // Cooldown names that never count, by lower case name
    private static final Set<String> IGNORED_COOLDOWNS = Set.of(
            // ProjectKorra: MetalClips' shoot and crush are parts of one use, which counts with the MetalClips cooldown
            "metalclips shoot", "metalclips crush",
            // IceBullet adds a cooldown per shot, its use counts with the IceBullet cooldown at the end
            "icebullet left", "icebullet right",
            // Only used when admins enable them: WaterSpout's hop and WaterArms' per attack cooldowns are parts of one use
            "spouthop", "waterarms_left", "waterarms_right",
            // Only used when admins enable it: Torrent's wave is part of the Torrent use, which counts when it ends
            "torrentwave",
            // JedCore: ElementSphereFire counts when it starts. MetalCables grab and slam are parts of a session.
            // DaggerThrow adds a short cooldown for every dagger in a volley
            "esfire", "metalcablesgrab", "metalcablesslam", "daggerthrowshot");

    // Cooldown names that belong to a differently named ability, by lower case cooldown name.
    // Only used when the cooldown wasn't added from inside an ability's code, which normally identifies the ability already
    private static final Map<String, String> COOLDOWN_ALIASES = Map.ofEntries(
            Map.entry("blazearc", "Blaze"), Map.entry("blazering", "Blaze"),
            Map.entry("raiseearthpillar", "RaiseEarth"), Map.entry("raiseearthwall", "RaiseEarth"),
            Map.entry("collapsepillar", "Collapse"), Map.entry("collapsewall", "Collapse"),
            Map.entry("metalclips magnet", "MetalClips"),
            Map.entry("surgewave", "Surge"), Map.entry("surgewall", "Surge"),
            Map.entry("phasechangefreeze", "PhaseChange"), Map.entry("phasechangemelt", "PhaseChange"),
            Map.entry("icespikeblast", "IceSpike"), Map.entry("icespikepillar", "IceSpike"), Map.entry("icespikepillarfield", "IceSpike"),
            Map.entry("waterspoutwave", "WaterSpout"));

    private static final Override DEFAULT = Override.trigger(Trigger.AUTO);


    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    // Called once for each use, with the player and the ability's name
    private final BiConsumer<Player, String> onUse;

    // Instance ids of END abilities the player started, waiting for them to end
    private final Set<Integer> endTracked = new HashSet<>();
    // END instances whose used field was true at some point while running
    private final Set<Integer> usedWhileRunning = new HashSet<>();
    private final Set<String> warnedFields = new HashSet<>();
    // Instance ids already counted, so their later cooldowns don't count again
    private final Set<Integer> creditedInstances = new HashSet<>();
    // Instance ids counted through a cooldown with a different name, so their own cooldown doesn't count again
    private final Set<Integer> creditedByOtherName = new HashSet<>();
    // Instance ids that started another ability while running
    private final Set<Integer> spawnedChild = new HashSet<>();
    // Abilities counted this tick from a cooldown added in their constructor, waiting to start so their instance can be marked
    private final Set<String> pendingConstructorCredits = new HashSet<>();

    // Uses counted this tick, so one use that starts several instances at once only counts once
    private final Set<String> countedThisTick = new HashSet<>();
    private long countedTick = -1;

    public PKAbilityUseTracker(BiConsumer<Player, String> onUse) {
        this.onUse = onUse;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCooldownAdded(PlayerCooldownChangeEvent event) {
        if (event.getResult() != PlayerCooldownChangeEvent.Result.ADDED) {
            return;
        }
        Player player = event.getPlayer().getPlayer();
        if (player == null) {
            return;
        }
        String cooldownName = event.getAbility();
        if (cooldownName == null || IGNORED_COOLDOWNS.contains(cooldownName.toLowerCase(Locale.ROOT))) {
            return;
        }

        // Adding a cooldown that's still running is part of the same use
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer != null && bPlayer.isOnCooldown(cooldownName)) {
            return;
        }

        // Some abilities add cooldowns under a sub name (e.g. "WaterSpoutWave" for WaterSpout)
        String aliasedName = COOLDOWN_ALIASES.getOrDefault(cooldownName.toLowerCase(Locale.ROOT), cooldownName);
        CoreAbility ability = null;
        Collection<? extends CoreAbility> runningInstances = List.of();
        boolean inConstructor = false;
        List<StackWalker.StackFrame> abilityFrames = findAbilityFrames(null, true);
        if (!abilityFrames.isEmpty()) {
            // Added from inside ability code, which tells us which ability the player actually used
            StackWalker.StackFrame credited = null;
            // 1. The ability the cooldown is named after, if its code is running (e.g. Suffocate's remove() when an enemy's hit
            //    ends it, or a cooldown added after its instance already stopped)
            for (StackWalker.StackFrame frame : abilityFrames) {
                CoreAbility frameAbility = CoreAbility.getAbility(frame.getDeclaringClass().asSubclass(CoreAbility.class));
                if (frameAbility != null && isOwnCooldown(frameAbility, cooldownName, aliasedName)) {
                    credited = frame;
                    break;
                }
            }
            // 2. Otherwise the outermost ability the player is running or creating that counts cooldowns
            //    (e.g. MeteorDash adding FireJet's cooldown, or SurgeWave launching from inside SurgeWall's constructor)
            if (credited == null) {
                for (int i = abilityFrames.size() - 1; i >= 0 && credited == null; i--) {
                    StackWalker.StackFrame frame = abilityFrames.get(i);
                    Class<? extends CoreAbility> frameClass = frame.getDeclaringClass().asSubclass(CoreAbility.class);
                    Trigger frameTrigger = OVERRIDES.getOrDefault(frameClass.getName(), DEFAULT).trigger();
                    boolean countsCooldowns = frameTrigger == Trigger.AUTO || frameTrigger == Trigger.COOLDOWN;
                    if (countsCooldowns && (frame.getMethodName().equals("<init>") || !CoreAbility.getAbilities(player, frameClass).isEmpty())) {
                        credited = frame;
                    }
                }
            }
            // 3. Only other players' abilities are running, so one of them put this cooldown on the player
            //    (e.g. a hit that disables their abilities). That isn't a use
            if (credited == null) {
                return;
            }
            Class<? extends CoreAbility> abilityClass = credited.getDeclaringClass().asSubclass(CoreAbility.class);
            ability = CoreAbility.getAbility(abilityClass);
            inConstructor = credited.getMethodName().equals("<init>");
            runningInstances = CoreAbility.getAbilities(player, abilityClass);
        } else {
            // 4. Added from a listener or manager
            ability = CoreAbility.getAbility(aliasedName);
        }

        if (ability == null || ability instanceof PassiveAbility) {
            return;
        }
        Override override = getOverride(ability);
        if (override.trigger() != Trigger.AUTO && override.trigger() != Trigger.COOLDOWN) {
            return;
        }

        // A cooldown with a different name than the ability (e.g. MeteorDash re-adding FireJet's cooldown every tick)
        // counts once for each instance, since checking whether it's already on cooldown doesn't catch repeats
        boolean ownName = isOwnCooldown(ability, cooldownName, aliasedName);
        if (!inConstructor && !runningInstances.isEmpty()) {
            if (!ownName) {
                boolean alreadyCredited = false;
                for (CoreAbility instance : runningInstances) {
                    alreadyCredited |= !creditedInstances.add(instance.getId());
                    creditedByOtherName.add(instance.getId());
                }
                if (alreadyCredited) {
                    return;
                }
            } else {
                // Its own cooldown, after a differently named cooldown already counted this instance (MeteorDash ending)
                for (CoreAbility instance : runningInstances) {
                    if (creditedByOtherName.contains(instance.getId())) {
                        return;
                    }
                }
                runningInstances.forEach(instance -> creditedInstances.add(instance.getId()));
            }
        } else if (inConstructor) {
            // Mark the instance as counted once it starts
            rollTick();
            pendingConstructorCredits.add(player.getUniqueId() + ":" + ability.getClass().getName());
        }
        countUse(player, creditedName(ability, override));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAbilityStart(AbilityStartEvent event) {
        if (!(event.getAbility() instanceof CoreAbility ability) || ability instanceof PassiveAbility) {
            return;
        }
        Player player = ability.getPlayer();
        if (player == null) {
            return;
        }
        // Its cooldown was already counted in its constructor, this tick
        rollTick();
        if (pendingConstructorCredits.remove(player.getUniqueId() + ":" + ability.getClass().getName())) {
            creditedInstances.add(ability.getId());
        }

        // Started from inside another ability's code: it's a child, part of that ability's use. Remember that the parent
        // spawned something, END overrides use that to tell a real use from a cancelled one
        boolean isChild = false;
        // NONE abilities are included here: an ability started by one (e.g. SpiritualProjection's parts) is still a child
        for (StackWalker.StackFrame frame : findAbilityFrames(ability.getClass(), false)) {
            isChild = true;
            for (CoreAbility parent : CoreAbility.getAbilities(player, frame.getDeclaringClass().asSubclass(CoreAbility.class))) {
                spawnedChild.add(parent.getId());
            }
        }

        Override override = getOverride(ability);
        Trigger trigger = override.trigger();
        if (trigger == Trigger.NONE || trigger == Trigger.COOLDOWN) {
            return;
        }
        if ((trigger == Trigger.AUTO || trigger == Trigger.END) && isChild) {
            return;
        }
        if (trigger == Trigger.END) {
            // Decided when it ends
            endTracked.add(ability.getId());
            return;
        }

        // Check the cooldown a tick later. Attribute changes like AvatarState's are applied after this event
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (trigger == Trigger.AUTO && ability.getCooldown() > 0) {
                // Counted when its cooldown is added instead
                return;
            }
            if (player.isOnline() && creditedInstances.add(ability.getId())) {
                countUse(player, creditedName(ability, override));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbilityProgress(AbilityProgressEvent event) {
        // Some fields are reset before the ability ends (HealingWaters stops "healing" once the target is at full health),
        // so remember if one was ever set while it ran
        if (!(event.getAbility() instanceof CoreAbility ability) || !endTracked.contains(ability.getId())
                || usedWhileRunning.contains(ability.getId())) {
            return;
        }
        if (wasUsed(ability, getOverride(ability))) {
            usedWhileRunning.add(ability.getId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbilityEnd(AbilityEndEvent event) {
        if (!(event.getAbility() instanceof CoreAbility ability)) {
            return;
        }
        // Removed a tick later, so a cooldown added while it's being removed still sees it as credited
        int id = ability.getId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            creditedInstances.remove(id);
            creditedByOtherName.remove(id);
            spawnedChild.remove(id);
        });
        if (!endTracked.remove(id)) {
            return;
        }
        Player player = ability.getPlayer();
        Override override = getOverride(ability);
        boolean used = usedWhileRunning.remove(id) || wasUsed(ability, override);
        if (player != null && player.isOnline() && used) {
            countUse(player, creditedName(ability, override));
        }
    }

    // Whether the cooldown is the ability's own, by name, credited name, or alias
    private boolean isOwnCooldown(CoreAbility ability, String cooldownName, String aliasedName) {
        String credited = creditedName(ability, getOverride(ability));
        return cooldownName.equalsIgnoreCase(ability.getName()) || cooldownName.equalsIgnoreCase(credited)
                || aliasedName.equalsIgnoreCase(ability.getName()) || aliasedName.equalsIgnoreCase(credited);
    }

    private Override getOverride(CoreAbility ability) {
        return OVERRIDES.getOrDefault(ability.getClass().getName(), DEFAULT);
    }

    private String creditedName(CoreAbility ability, Override override) {
        return override.creditAs() != null ? override.creditAs() : ability.getName();
    }

    // For END overrides with used fields, whether any of them shows the ability was really used
    private boolean wasUsed(CoreAbility ability, Override override) {
        if (override.usedField() == null) {
            return true;
        }
        for (String fieldName : override.usedField().split("\\|")) {
            if (fieldName.equals(SPAWNED_CHILD)) {
                if (spawnedChild.contains(ability.getId())) {
                    return true;
                }
                continue;
            }
            Boolean value = readBooleanField(ability, fieldName);
            if (value == null) {
                // The ability changed in an update. Count every end rather than never counting it
                if (warnedFields.add(ability.getClass().getName() + "." + fieldName)) {
                    plugin.getLogger().warning("Could not read " + ability.getClass().getSimpleName() + "." + fieldName
                            + " to check if it was used, every " + ability.getName() + " will count as a use");
                }
                return true;
            }
            if (value) {
                return true;
            }
        }
        return false;
    }

    // Reads a boolean field from the ability or a parent class, or null if it doesn't exist
    private static Boolean readBooleanField(CoreAbility ability, String fieldName) {
        for (Class<?> clazz = ability.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getBoolean(ability);
            } catch (NoSuchFieldException e) {
                // Check the parent class
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    // Clears the per tick state when a new tick starts. Constructors and starts happen in the same tick
    private void rollTick() {
        long tick = CoreAbility.getCurrentTick();
        if (tick != countedTick) {
            countedThisTick.clear();
            pendingConstructorCredits.clear();
            countedTick = tick;
        }
    }

    // Counts a use, at most once per player and ability each tick
    private void countUse(Player player, String abilityName) {
        rollTick();
        if (countedThisTick.add(player.getUniqueId() + ":" + abilityName.toLowerCase(Locale.ROOT))) {
            onUse.accept(player, abilityName);
        }
    }

    /***
     * Finds the frames of registered ability classes in the current call stack, innermost first.
     * @param ignoring an ability class to skip, along with its parent classes, or null
     * @param skipNone whether to skip abilities overridden to NONE, for picking which ability gets credit (they never do)
     */
    private static List<StackWalker.StackFrame> findAbilityFrames(Class<?> ignoring, boolean skipNone) {
        return STACK_WALKER.walk(frames -> frames
                .filter(frame -> {
                    Class<?> clazz = frame.getDeclaringClass();
                    if (!CoreAbility.class.isAssignableFrom(clazz) || clazz == CoreAbility.class
                            || (ignoring != null && clazz.isAssignableFrom(ignoring))) {
                        return false;
                    }
                    CoreAbility registered = CoreAbility.getAbility(clazz.asSubclass(CoreAbility.class));
                    return registered != null && (!skipNone || OVERRIDES.getOrDefault(clazz.getName(), DEFAULT).trigger() != Trigger.NONE);
                })
                .toList());
    }
}
