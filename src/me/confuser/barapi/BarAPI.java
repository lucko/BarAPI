package me.confuser.barapi;

import me.confuser.barapi.nms.FakeDragon;
import me.confuser.barapi.nms.v1_8Fake;
import me.lucko.helper.bossbar.BossBar;
import me.lucko.helper.bossbar.BossBarColor;
import me.lucko.helper.bossbar.BossBarFactory;
import me.lucko.helper.bossbar.BossBarStyle;
import me.lucko.helper.plugin.ExtendedJavaPlugin;
import me.lucko.helper.plugin.ap.Plugin;
import me.lucko.helper.text.Text;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.ServicePriority;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Allows plugins to safely set a health bar message.
 *
 * @author James Mortemore
 */
@Plugin(name = "BarAPI", hardDepends = {"helper", "ViaVersion"})
public class BarAPI extends ExtendedJavaPlugin implements Listener, BossBarFactory {

    private static boolean useSpigotHack = false;
    private Map<UUID, FakeDragon> players = new HashMap<>();
    private Map<UUID, Integer> timers = new HashMap<>();

    public static boolean useSpigotHack() {
        return useSpigotHack;
    }

    private static String cleanMessage(String message) {
        if (message.length() > 64)
            message = message.substring(0, 63);

        return message;
    }

    private static BlockFace getDirection(Location loc) {
        float dir = Math.round(loc.getYaw() / 90);
        if (dir == -4 || dir == 0 || dir == 4)
            return BlockFace.SOUTH;
        if (dir == -1 || dir == 3)
            return BlockFace.EAST;
        if (dir == -2 || dir == 2)
            return BlockFace.NORTH;
        if (dir == -3 || dir == 1)
            return BlockFace.WEST;
        return null;
    }

    @Override
    public void enable() {
        getConfig().options().copyDefaults(true);
        saveConfig();

        useSpigotHack = getConfig().getBoolean("useSpigotHack", false);

        if (!useSpigotHack()) {
            if (v1_8Fake.isUsable()) {
                useSpigotHack = true;
                Util.detectVersion();
                getLogger().info("Detected spigot hack, enabling fake 1.8");
            }
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (useSpigotHack) {
            getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
                for (UUID uuid : this.players.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    Util.sendPacket(p, this.players.get(uuid).getTeleportPacket(getDragonLocation(p.getLocation())));
                }
            }, 0L, 5L);
        }

        // provide helper boss bar service
        BossBarFactory service = getService(BossBarFactory.class);
        provideService(BossBarFactory.class, new MixedBossBarFactory(this, service), ServicePriority.High);
    }

    @Override
    public void disable() {
        for (Player player : getServer().getOnlinePlayers()) {
            quit(player);
        }

        this.players.clear();

        for (int timerID : this.timers.values()) {
            Bukkit.getScheduler().cancelTask(timerID);
        }

        this.timers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void PlayerLoggout(PlayerQuitEvent event) {
        quit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        quit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(final PlayerTeleportEvent event) {
        handleTeleport(event.getPlayer(), event.getTo().clone());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(final PlayerRespawnEvent event) {
        handleTeleport(event.getPlayer(), event.getRespawnLocation().clone());
    }

    private void handleTeleport(final Player player, final Location loc) {
        if (!hasBar(player)) {
            return;
        }

        final FakeDragon oldDragon = getDragon(player, "");

        Bukkit.getScheduler().runTaskLater(this, () -> {
            // Check if the player still has a dragon after the two ticks! ;)
            if (!hasBar(player))
                return;

            float health = oldDragon.health;
            String message = oldDragon.name;

            Util.sendPacket(player, getDragon(player, "").getDestroyPacket());

            this.players.remove(player.getUniqueId());

            FakeDragon dragon = addDragon(player, loc, message);
            dragon.health = health;

            sendDragon(dragon, player);
        }, 2L);
    }

    private void quit(Player player) {
        removeBar(player);
    }

    /**
     * Set a message for the given player.<br>
     * It will remain there until the player logs off or another plugin overrides it.<br>
     * This method will show a health bar using the given percentage value and will cancel any running timers.
     *
     * @param player  The player who should see the given message.
     * @param message The message shown to the player.<br>
     *                Due to limitations in Minecraft this message cannot be longer than 64 characters.<br>
     *                It will be cut to that size automatically.
     * @param percent The percentage of the health bar filled.<br>
     *                This value must be between 0F (inclusive) and 100F (inclusive).
     * @throws IllegalArgumentException If the percentage is not within valid bounds.
     */
    public void setMessage(Player player, String message, float percent) {
        Validate.isTrue(0F <= percent && percent <= 100F, "Percent must be between 0F and 100F, but was: ", percent);

        if (hasBar(player)) {
            removeBar(player);
        }

        FakeDragon dragon = getDragon(player, message);

        dragon.name = cleanMessage(message);
        dragon.health = (percent / 100f) * dragon.getMaxHealth();

        cancelTimer(player);

        sendDragon(dragon, player);
    }

    /**
     * Checks whether the given player has a bar.
     *
     * @param player The player who should be checked.
     * @return True, if the player has a bar, False otherwise.
     */
    public boolean hasBar(Player player) {
        return this.players.get(player.getUniqueId()) != null;
    }

    /**
     * Removes the bar from the given player.<br>
     * If the player has no bar, this method does nothing.
     *
     * @param player The player whose bar should be removed.
     */
    public void removeBar(Player player) {
        if (!hasBar(player))
            return;

        FakeDragon dragon = getDragon(player, "");
        Util.sendPacket(player, dragon.getDestroyPacket());
        this.players.remove(player.getUniqueId());
        cancelTimer(player);
    }

    private void cancelTimer(Player player) {
        Integer timerID = this.timers.remove(player.getUniqueId());

        if (timerID != null) {
            Bukkit.getScheduler().cancelTask(timerID);
        }
    }

    private void sendDragon(FakeDragon dragon, Player player) {
        Util.sendPacket(player, dragon.getMetaPacket(dragon.getWatcher()));
        Util.sendPacket(player, dragon.getTeleportPacket(getDragonLocation(player.getLocation())));
    }

    private FakeDragon getDragon(Player player, String message) {
        if (hasBar(player)) {
            return this.players.get(player.getUniqueId());
        } else
            return addDragon(player, cleanMessage(message));
    }

    private FakeDragon addDragon(Player player, String message) {
        FakeDragon dragon = Util.newDragon(message, getDragonLocation(player.getLocation()));
        Util.sendPacket(player, dragon.getSpawnPacket());
        this.players.put(player.getUniqueId(), dragon);
        return dragon;
    }

    private FakeDragon addDragon(Player player, Location loc, String message) {
        FakeDragon dragon = Util.newDragon(message, getDragonLocation(loc));
        Util.sendPacket(player, dragon.getSpawnPacket());
        this.players.put(player.getUniqueId(), dragon);
        return dragon;
    }

    private Location getDragonLocation(Location loc) {
        if (Util.isBelowGround) {
            loc.subtract(0, 300, 0);
            return loc;
        }

        float pitch = loc.getPitch();

        if (pitch >= 55) {
            loc.add(0, -300, 0);
        } else if (pitch <= -55) {
            loc.add(0, 300, 0);
        } else {
            loc = loc.getBlock().getRelative(getDirection(loc), getServer().getViewDistance() * 16).getLocation();
        }

        return loc;
    }

    @Nonnull
    @Override
    public BossBar newBossBar() {
        return new BarApiBossBar();
    }

    private final class BarApiBossBar implements BossBar {
        private String title = "null";
        private double progress = 1d;
        private boolean visible = true;
        private final Set<Player> players = new HashSet<>();

        private void update() {
            for (Player p : this.players) {
                update(p);
            }
        }

        private void update(Player p) {
            setMessage(p, this.title, (float) (this.progress * 100d));
        }

        @Nonnull
        @Override
        public String title() {
            return this.title;
        }

        @Nonnull
        @Override
        public BossBar title(@Nonnull String title) {
            this.title = Text.colorize(title);
            update();
            return this;
        }

        @Override
        public double progress() {
            return this.progress;
        }

        @Nonnull
        @Override
        public BossBar progress(double progress) {
            this.progress = progress;
            update();
            return this;
        }

        @Nonnull
        @Override
        public BossBarColor color() {
            return BossBarColor.defaultColor();
        }

        @Nonnull
        @Override
        public BossBar color(@Nonnull BossBarColor color) {
            return this;
        }

        @Nonnull
        @Override
        public BossBarStyle style() {
            return BossBarStyle.defaultStyle();
        }

        @Nonnull
        @Override
        public BossBar style(@Nonnull BossBarStyle style) {
            return this;
        }

        @Override
        public boolean visible() {
            return this.visible;
        }

        @Nonnull
        @Override
        public BossBar visible(boolean visible) {
            this.visible = visible;

            if (!visible) {
                for (Player p : this.players) {
                    removeBar(p);
                }
            } else {
                update();
            }

            return this;
        }

        @Nonnull
        @Override
        public List<Player> players() {
            return new ArrayList<>(this.players);
        }

        @Override
        public void addPlayer(@Nonnull Player player) {
            if (this.players.add(player)) {
                update(player);
            }
        }

        @Override
        public void removePlayer(@Nonnull Player player) {
            if (this.players.remove(player)) {
                removeBar(player);
            }
        }

        @Override
        public void removeAll() {
            for (Player p : this.players) {
                removePlayer(p);
            }
        }

        @Override
        public void close() {
            removeAll();
        }
    }
}
