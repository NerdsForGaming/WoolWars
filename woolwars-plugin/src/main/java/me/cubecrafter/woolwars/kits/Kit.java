package me.cubecrafter.woolwars.kits;

import lombok.Getter;
import me.cubecrafter.woolwars.config.Messages;
import me.cubecrafter.woolwars.team.GameTeam;
import me.cubecrafter.woolwars.utils.ItemBuilder;
import me.cubecrafter.woolwars.utils.TextUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Kit {

    private final String id;
    private final String displayName;
    private final boolean helmetEnabled, chestplateEnabled, leggingsEnabled, bootsEnabled;
    private final Map<ItemStack, Integer> contents = new HashMap<>();
    private final Ability ability;

    public Kit(String id, YamlConfiguration kitConfig) {
        this.id = id;
        this.displayName = TextUtil.color(kitConfig.getString("displayname"));
        this.helmetEnabled = kitConfig.getBoolean("armor.helmet");
        this.chestplateEnabled = kitConfig.getBoolean("armor.chestplate");
        this.leggingsEnabled = kitConfig.getBoolean("armor.leggings");
        this.bootsEnabled = kitConfig.getBoolean("armor.boots");
        this.ability = new Ability(kitConfig);
        for (String section : kitConfig.getConfigurationSection("items").getKeys(false)) {
            ItemStack item = ItemBuilder.fromConfig(kitConfig.getConfigurationSection("items." + section)).setUnbreakable(true).build();
            contents.put(item, kitConfig.getInt("items." + section + ".slot"));
        }
    }

    public void addToPlayer(Player player, GameTeam team) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        TextUtil.sendMessage(player, Messages.KIT_SELECTED.getAsString().replace("{displayname}", displayName));
        if (helmetEnabled) player.getInventory().setHelmet(new ItemBuilder("LEATHER_HELMET").setColor(team.getTeamColor().getColor()).build());
        if (chestplateEnabled) player.getInventory().setChestplate(new ItemBuilder("LEATHER_CHESTPLATE").setColor(team.getTeamColor().getColor()).build());
        if (leggingsEnabled) player.getInventory().setLeggings(new ItemBuilder("LEATHER_LEGGINGS").setColor(team.getTeamColor().getColor()).build());
        if (bootsEnabled) player.getInventory().setBoots(new ItemBuilder("LEATHER_BOOTS").setColor(team.getTeamColor().getColor()).build());
        for (Map.Entry<ItemStack, Integer> entry : contents.entrySet()) {
            if (entry.getKey().getType().toString().contains("WOOL")) {
                ItemStack oldWool = entry.getKey();
                ItemMeta meta = oldWool.getItemMeta();
                ItemStack wool = new ItemBuilder(team.getTeamColor().getWoolMaterial()).setAmount(oldWool.getAmount()).setDisplayName(meta.getDisplayName()).setLore(meta.getLore()).build();
                player.getInventory().setItem(entry.getValue(), wool);
            } else {
                player.getInventory().setItem(entry.getValue(), entry.getKey());
            }
        }
        player.getInventory().setItem(ability.getSlot(), ability.getItem());
    }

}
