package me.cubecrafter.woolwars.powerup;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import me.cubecrafter.woolwars.WoolWars;
import me.cubecrafter.woolwars.arena.GameArena;
import me.cubecrafter.woolwars.config.Configuration;
import me.cubecrafter.woolwars.utils.ArenaUtil;
import me.cubecrafter.woolwars.utils.TextUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter
public class PowerUp {

    private ArmorStand armorStand;
    private final List<ArmorStand> holoLines = new ArrayList<>();
    private final Location location;
    private final GameArena arena;
    private PowerUpData data;
    private int rotation = 0;
    private boolean active = false;

    public PowerUp(Location location, GameArena arena) {
        this.arena = arena;
        this.location = location;
    }

    public void use(Player player) {
        remove();
        ArenaUtil.playSound(player, Configuration.SOUNDS_POWERUP_COLLECTED.getAsString());
        List<String> toReplace = Arrays.asList("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_PICKAXE", "_SWORD", "_AXE", "_SHOVEL", "_HOE");
        for (ItemStack item : data.getItems()) {
            String material = item.getType().toString();
            String end = toReplace.stream().filter(material::endsWith).findAny().orElse(null);
            if (end == null) {
                player.getInventory().addItem(item);
            } else {
                boolean found = false;
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack == null || stack.getType().equals(Material.AIR)) continue;
                    if (stack.getType().toString().endsWith(end)) {
                        player.getInventory().setItem(i, item);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    for (int i = 0; i < player.getInventory().getArmorContents().length; i++) {
                        ItemStack stack = player.getInventory().getArmorContents()[i];
                        if (stack == null || stack.getType().equals(Material.AIR)) continue;
                        if (stack.getType().toString().endsWith(end)) {
                            switch (i) {
                                case 0:
                                    player.getInventory().setBoots(item);
                                    break;
                                case 1:
                                    player.getInventory().setLeggings(item);
                                    break;
                                case 2:
                                    player.getInventory().setChestplate(item);
                                    break;
                                case 3:
                                    player.getInventory().setHelmet(item);
                                    break;
                            }
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    player.getInventory().addItem(item);
                }
            }
        }
        for (PotionEffect effect : data.getEffects()) {
            player.addPotionEffect(effect, true);
        }
    }

    public void spawn() {
        data = WoolWars.getInstance().getPowerupManager().getRandom();
        armorStand = spawnArmorStand(null, location);
        if (XMaterial.PLAYER_HEAD.parseMaterial().equals(data.getDisplayedItem().getType())) {
            armorStand.getEquipment().setHelmet(data.getDisplayedItem());
        } else {
            armorStand.getEquipment().setItemInHand(data.getDisplayedItem());
            armorStand.setRightArmPose(new EulerAngle(Math.toRadians(280), Math.toRadians(270), 0));
        }
        setupHolo();
        active = true;
    }

    public void remove() {
        if (!active) return;
        active = false;
        armorStand.remove();
        holoLines.forEach(Entity::remove);
        holoLines.clear();
    }

    public void rotate() {
        if (!active) return;
        Location loc = armorStand.getLocation();
        loc.setYaw(rotation);
        armorStand.teleport(loc);
        rotation += 3;
    }

    private void setupHolo() {
        double offset = 2;
        List<String> reversed = new ArrayList<>(data.getHoloLines());
        Collections.reverse(reversed);
        for (String line : reversed) {
            holoLines.add(spawnArmorStand(line, location.clone().add(0, offset, 0)));
            offset += 0.3;
        }
    }

    private ArmorStand spawnArmorStand(String name, Location location) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setGravity(false);
        stand.setRemoveWhenFarAway(false);
        stand.setVisible(false);
        stand.setCanPickupItems(false);
        stand.setArms(false);
        stand.setBasePlate(false);
        stand.setMarker(true);
        if (name != null) {
            stand.setCustomName(TextUtil.color(name));
            stand.setCustomNameVisible(true);
        }
        return stand;
    }

}
