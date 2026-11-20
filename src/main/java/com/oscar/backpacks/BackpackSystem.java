package com.oscar.backpacks;

import org.bukkit.event.block.Action;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

/**
 * BackpackSystem para Paper 26.3.
 * Las mochilas son objetos vanilla marcados con PersistentDataContainer.
 * El contenido se guarda por UUID en backpacks.yml.
 */
public final class BackpackSystem extends JavaPlugin implements Listener {
    private NamespacedKey idKey, tierKey, moduleKey, creatorKey;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final Set<UUID> currentlyOpen = new HashSet<>();
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, UUID> equippedBackpacks = new HashMap<>();
    private final Map<UUID, UUID> displayEntities = new HashMap<>();
    private BukkitRunnable displayTask;

    private static final String[] TIERS = {"basic", "iron", "gold", "diamond", "netherite"};
    private static final int[] DEFAULT_SIZES = {9, 27, 36, 54, 90};
    private static final String[] NAMES = {"Básica", "Hierro", "Oro", "Diamante", "Netherita"};
    private static final Material[] UPGRADE_MATERIALS = {null, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.NETHERITE_INGOT};

    @Override public void onEnable() {
        saveDefaultConfig();
        idKey = new NamespacedKey(this, "backpack_id");
        tierKey = new NamespacedKey(this, "backpack_tier");
        moduleKey = new NamespacedKey(this, "upgrade_module");
        creatorKey = new NamespacedKey(this, "backpack_creator");
        dataFile = new File(getDataFolder(), "backpacks.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        getServer().getPluginManager().registerEvents(this, this);
        registerRecipes();
        loadEquippedBackpacks();
        startDisplayTask();
        getLogger().info("BackpackSystem activado. Niveles: 9/27/36/54/90.");
    }

    @Override public void onDisable() {
        for (var entry : openInventories.entrySet()) {
            Inventory inventory = entry.getValue();
            if (inventory.getHolder(false) instanceof BackpackHolder holder) {
                int tier = tierFromOpenBackpack(holder.backpackId());
                if (tier >= 0) saveVisiblePage(holder, inventory, tier);
            }
        }
        if (displayTask != null) displayTask.cancel();
        for (UUID entityId : displayEntities.values()) {
            var entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
        displayEntities.clear();
        persist();
    }

    private void registerRecipes() {
        // Receta básica provisional y editable en este método.
        ItemStack basic = createBackpack(0, UUID.randomUUID());
        NamespacedKey basicKey = new NamespacedKey(this, "backpack_basic");
        getServer().removeRecipe(basicKey);
        ShapedRecipe basicRecipe = new ShapedRecipe(basicKey, basic);
        basicRecipe.shape("L L", "LCL", "L L");
        basicRecipe.setIngredient('L', Material.LEATHER);
        basicRecipe.setIngredient('C', Material.CHEST);
        getServer().addRecipe(basicRecipe);

        // Módulo universal provisional: cuatro pepitas de hierro y cuatro de redstone.
        ItemStack module = createModule();
        NamespacedKey moduleKeyRecipe = new NamespacedKey(this, "upgrade_module");
        getServer().removeRecipe(moduleKeyRecipe);
        ShapedRecipe moduleRecipe = new ShapedRecipe(moduleKeyRecipe, module);
        moduleRecipe.shape("IRI", "R R", "IRI");
        moduleRecipe.setIngredient('I', Material.IRON_NUGGET);
        moduleRecipe.setIngredient('R', Material.REDSTONE);
        getServer().addRecipe(moduleRecipe);

        // Recetas de mejora: siete minerales + módulo (arriba-centro) + mochila (centro).
        for (int next = 1; next < TIERS.length; next++) {
            NamespacedKey upgradeKey = new NamespacedKey(this, "upgrade_" + TIERS[next]);
            getServer().removeRecipe(upgradeKey);
            ShapedRecipe upgrade = new ShapedRecipe(upgradeKey, createBackpack(next, UUID.randomUUID()));
            upgrade.shape("IMI", "IBI", "III");
            upgrade.setIngredient('I', UPGRADE_MATERIALS[next]);
            upgrade.setIngredient('M', Material.AMETHYST_SHARD);
            upgrade.setIngredient('B', Material.PAPER);
            getServer().addRecipe(upgrade);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapedRecipe recipe) || !recipe.getKey().getNamespace().equals(getName().toLowerCase(Locale.ROOT))) return;
        if (recipe.getKey().getKey().equals("backpack_basic")) {
            // Cada mochila básica fabricada debe recibir un UUID propio.
            event.getInventory().setResult(createBackpack(0, UUID.randomUUID(), event.getView().getPlayer().getName()));
            return;
        }
        if (!recipe.getKey().getKey().startsWith("upgrade_")) return;
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (matrix.length != 9) { event.getInventory().setResult(null); return; }
        // Centro superior = módulo; centro = mochila; los otros siete espacios = mineral.
        ItemStack module = matrix[1];
        ItemStack bag = matrix[4];
        int oldTier = tier(bag);
        if (!isModule(module) || oldTier < 0 || oldTier >= 4) { event.getInventory().setResult(null); return; }
        Material required = UPGRADE_MATERIALS[oldTier + 1];
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 1 || slot == 4) continue;
            ItemStack ingredient = matrix[slot];
            if (ingredient == null || ingredient.getType() != required || ingredient.getAmount() < 1) {
                event.getInventory().setResult(null);
                return;
            }
        }
        UUID id = backpackId(bag);
        event.getInventory().setResult(createBackpack(oldTier + 1, id, backpackCreator(bag)));
    }

    public ItemStack createBackpack(int tier, UUID id) {
        return createBackpack(tier, id, "Servidor");
    }

    private ItemStack createBackpack(int tier, UUID id, String creator) {
        // PAPER no puede colocarse como bloque. La vista previa se añade mediante
        // el componente CONTAINER de Paper, sin convertir la mochila en una shulker.
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Mochila " + NAMES[tier]);
        meta.setLore(List.of(ChatColor.GRAY + "Creada por: " + creator));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toString());
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(creatorKey, PersistentDataType.STRING, creator);
        meta.setItemModel(new NamespacedKey("backpacks", "backpack_" + TIERS[tier]));
        item.setItemMeta(meta);

        // La vista previa viaja dentro del propio objeto: otros jugadores también la ven.
        // Es solo una copia visual; el inventario real continúa guardándose por UUID en backpacks.yml.
        List<ItemStack> preview = new ArrayList<>();
        for (ItemStack stack : loadContents(id, sizeForTier(tier))) {
            if (stack != null && stack.getType() != Material.AIR) preview.add(stack.clone());
        }
        if (!preview.isEmpty()) {
            item.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(preview));
        } else {
            item.unsetData(DataComponentTypes.CONTAINER);
        }
        return item;
    }

    private String backpackCreator(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "Desconocido";
        String creator = item.getItemMeta().getPersistentDataContainer().get(creatorKey, PersistentDataType.STRING);
        return creator == null || creator.isBlank() ? "Desconocido" : creator;
    }

    /**
     * Actualiza el objeto mochila del jugador con una copia reciente de la vista previa.
     * La copia viaja con el objeto al intercambiarlo o entregarlo a otro jugador.
     */
    private void refreshBackpackLore(Player player, UUID id) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack candidate = contents[slot];
            if (id.equals(backpackId(candidate))) {
                inventory.setItem(slot, createBackpack(tier(candidate), id, backpackCreator(candidate)));
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        if (id.equals(backpackId(offhand))) {
            inventory.setItemInOffHand(createBackpack(tier(offhand), id, backpackCreator(offhand)));
        }
    }

    private int sizeForTier(int tier) {
        // Los tamaños se pueden editar en plugins/BackpackSystem/config.yml.
        return getConfig().getInt("levels." + TIERS[tier], DEFAULT_SIZES[tier]);
    }

    private ItemStack createModule() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Módulo de mejora");
        meta.setLore(List.of(ChatColor.GRAY + "Componente universal para mochilas"));
        meta.getPersistentDataContainer().set(moduleKey, PersistentDataType.BYTE, (byte) 1);
        meta.setItemModel(new NamespacedKey("backpacks", "upgrade_module"));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isModule(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(moduleKey, PersistentDataType.BYTE);
    }

    private int tier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        Integer value = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        return value == null ? -1 : value;
    }

    private UUID backpackId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ex) { return null; }
    }

    private boolean isBackpack(ItemStack item) { return backpackId(item) != null && tier(item) >= 0; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isBackpack(item)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID id = backpackId(item);
        if (player.isSneaking()) {
            toggleEquippedBackpack(player, item);
            return;
        }
        if (currentlyOpen.contains(id)) {
            player.sendMessage(ChatColor.RED + "Esta mochila ya está abierta por otro jugador.");
            return;
        }
        int t = tier(item);
        BackpackHolder holder = new BackpackHolder(id);
        currentlyOpen.add(id);
        refreshBackpackLore(player, id);
        openBackpackPage(player, holder, t);
    }

    private void toggleEquippedBackpack(Player player, ItemStack backpack) {
        UUID playerId = player.getUniqueId();
        UUID backpackId = backpackId(backpack);
        UUID currentlyEquipped = equippedBackpacks.get(playerId);
        if (backpackId.equals(currentlyEquipped)) {
            unequipBackpack(playerId, true);
            player.sendMessage(ChatColor.YELLOW + "Te quitaste la mochila.");
            return;
        }
        if (currentlyEquipped != null) unequipBackpack(playerId, false);
        equippedBackpacks.put(playerId, backpackId);
        data.set("equipped." + playerId, backpackId.toString());
        persist();
        spawnBackpackDisplay(player, backpack);
        player.sendMessage(ChatColor.GREEN + "Mochila equipada. Agáchate y haz clic derecho de nuevo para quitártela.");
    }

    private void unequipBackpack(UUID playerId, boolean clearSaved) {
        equippedBackpacks.remove(playerId);
        UUID entityId = displayEntities.remove(playerId);
        if (entityId != null) {
            var entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
        if (clearSaved) {
            data.set("equipped." + playerId, null);
            persist();
        }
    }

    private void loadEquippedBackpacks() {
        if (!data.isConfigurationSection("equipped")) return;
        for (String key : Objects.requireNonNull(data.getConfigurationSection("equipped")).getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                UUID backpackId = UUID.fromString(data.getString("equipped." + key, ""));
                equippedBackpacks.put(playerId, backpackId);
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Registro de mochila equipada inválido para " + key);
            }
        }
    }

    private ItemStack findOwnedBackpack(Player player, UUID id) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (id.equals(backpackId(stack))) return stack;
        }
        return null;
    }

    private void spawnBackpackDisplay(Player player, ItemStack backpack) {
        UUID old = displayEntities.remove(player.getUniqueId());
        if (old != null) {
            var previous = Bukkit.getEntity(old);
            if (previous != null) previous.remove();
        }
        Location location = backpackDisplayLocation(player);
        ItemDisplay display = player.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(backpack.clone());
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistent(false);
        });
        displayEntities.put(player.getUniqueId(), display.getUniqueId());
    }

    private Location backpackDisplayLocation(Player player) {
        Location base = player.getLocation();
        org.bukkit.util.Vector backward = base.getDirection().setY(0).normalize().multiply(-0.34);
        if (backward.lengthSquared() < 0.001) backward = new org.bukkit.util.Vector(0, 0, 0.34);
        return base.add(backward).add(0, 0.85, 0);
    }

    private void startDisplayTask() {
        displayTask = new BukkitRunnable() {
            @Override public void run() {
                for (UUID playerId : new HashSet<>(equippedBackpacks.keySet())) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline() || player.isDead()) continue;
                    UUID backpackId = equippedBackpacks.get(playerId);
                    ItemStack backpack = findOwnedBackpack(player, backpackId);
                    if (backpack == null) {
                        unequipBackpack(playerId, true);
                        continue;
                    }
                    UUID entityId = displayEntities.get(playerId);
                    org.bukkit.entity.Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
                    if (!(entity instanceof ItemDisplay display) || !display.isValid() || display.getWorld() != player.getWorld()) {
                        spawnBackpackDisplay(player, backpack);
                    } else {
                        display.teleport(backpackDisplayLocation(player));
                        if (!display.getItemStack().isSimilar(backpack)) display.setItemStack(backpack.clone());
                    }
                }
            }
        };
        displayTask.runTaskTimer(this, 1L, 1L);
    }

    @EventHandler public void onBackpackJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            UUID backpackId = equippedBackpacks.get(player.getUniqueId());
            if (backpackId == null) return;
            ItemStack backpack = findOwnedBackpack(player, backpackId);
            if (backpack == null) unequipBackpack(player.getUniqueId(), true);
            else spawnBackpackDisplay(player, backpack);
        }, 2L);
    }

    @EventHandler public void onBackpackQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        UUID entityId = displayEntities.remove(playerId);
        if (entityId != null) {
            var entity = Bukkit.getEntity(entityId);
            if (entity != null) entity.remove();
        }
        // El registro de equipamiento se conserva para restaurarlo al volver a entrar.
    }

    @EventHandler public void onBackpackRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            UUID backpackId = equippedBackpacks.get(player.getUniqueId());
            if (backpackId == null) return;
            ItemStack backpack = findOwnedBackpack(player, backpackId);
            if (backpack == null) unequipBackpack(player.getUniqueId(), true);
            else spawnBackpackDisplay(player, backpack);
        }, 2L);
    }

    private void openBackpackPage(Player player, BackpackHolder holder, int tier) {
        int total = sizeForTier(tier);
        boolean paged = total > 54;
        int visible = paged ? (holder.page() == 0 ? 45 : total - 45) : total;
        String title = getConfig().getString("settings.inventory-title", "Mochila %tier%").replace("%tier%", NAMES[tier]);
        if (paged) title += holder.page() == 0 ? " (1/2)" : " (2/2)";
        Inventory inventory = Bukkit.createInventory(holder, paged ? 54 : total, ChatColor.translateAlternateColorCodes('&', title));
        holder.inventory(inventory);
        ItemStack[] all = loadContents(holder.backpackId(), total);
        int offset = paged && holder.page() == 1 ? 45 : 0;
        for (int i = 0; i < visible; i++) inventory.setItem(i, all[offset + i]);
        if (paged) {
            // Reservamos todos los espacios que no son almacenamiento para evitar
            // que se coloquen objetos en slots que no se guardan.
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta fillerMeta = filler.getItemMeta();
            fillerMeta.setDisplayName(ChatColor.GRAY + " ");
            filler.setItemMeta(fillerMeta);
            int firstReservedSlot = visible;
            for (int slot = firstReservedSlot; slot < 54; slot++) {
                inventory.setItem(slot, filler);
            }

            ItemStack nav = new ItemStack(Material.ARROW);
            ItemMeta meta = nav.getItemMeta();
            meta.setDisplayName(holder.page() == 0 ? ChatColor.GREEN + "Siguiente página" : ChatColor.YELLOW + "Página anterior");
            nav.setItemMeta(meta);
            inventory.setItem(49, nav);
        }
        openInventories.put(holder.backpackId(), inventory);
        player.openInventory(inventory);
        currentlyOpen.add(holder.backpackId());
    }

    private void saveVisiblePage(BackpackHolder holder, Inventory inventory, int tier) {
        int total = sizeForTier(tier);
        ItemStack[] all = loadContents(holder.backpackId(), total);
        boolean paged = total > 54;
        int offset = paged && holder.page() == 1 ? 45 : 0;
        int visible = paged ? (holder.page() == 0 ? 45 : total - 45) : total;
        for (int i = 0; i < visible; i++) all[offset + i] = inventory.getItem(i);
        saveContents(holder.backpackId(), all);
        persist();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BackpackHolder holder)) return;
        if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            int tier = tierFromOpenBackpack(holder.backpackId());
            if (tier >= 0 && sizeForTier(tier) > 54) {
                int contentSlots = holder.page() == 0 ? 45 : sizeForTier(tier) - 45;
                if (event.getRawSlot() >= contentSlots) {
                    event.setCancelled(true);
                    if (event.getRawSlot() == 49) {
                        saveVisiblePage(holder, event.getView().getTopInventory(), tier);
                        holder.page(holder.page() == 0 ? 1 : 0);
                        openBackpackPage((Player) event.getWhoClicked(), holder, tier);
                    }
                    return;
                }
            }
        }
        ItemStack current = event.getCurrentItem();
        if (getConfig().getBoolean("settings.prevent-nesting", true) && isBackpack(current)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(ChatColor.RED + "No puedes guardar una mochila dentro de otra.");
        }
        if (event.isShiftClick() && isBackpack(event.getCurrentItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BackpackHolder holder)) return;

        int tier = tierFromOpenBackpack(holder.backpackId());
        if (tier >= 0 && sizeForTier(tier) > 54) {
            int contentSlots = holder.page() == 0 ? 45 : sizeForTier(tier) - 45;
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= contentSlots && rawSlot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (!getConfig().getBoolean("settings.prevent-nesting", true)) return;
        for (ItemStack item : event.getNewItems().values()) {
            if (isBackpack(item)) { event.setCancelled(true); break; }
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BackpackHolder holder)) return;
        int tier = tierFromOpenBackpack(holder.backpackId());
        if (tier >= 0) saveVisiblePage(holder, event.getInventory(), tier);
        openInventories.remove(holder.backpackId());
        currentlyOpen.remove(holder.backpackId());
        persist();
        if (event.getPlayer() instanceof Player player) refreshBackpackLore(player, holder.backpackId());
    }

    private int tierFromOpenBackpack(UUID id) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (id.equals(backpackId(item))) return tier(item);
            }
        }
        return -1;
    }

    private ItemStack[] loadContents(UUID id, int size) {
        String encoded = data.getString("backpacks." + id);
        if (encoded == null) return new ItemStack[size];
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            Object obj = in.readObject();
            ItemStack[] old = (ItemStack[]) obj;
            ItemStack[] result = new ItemStack[size];
            System.arraycopy(old, 0, result, 0, Math.min(old.length, size));
            return result;
        } catch (Exception ex) {
            getLogger().severe("No se pudo cargar la mochila " + id + ": " + ex.getMessage());
            return new ItemStack[size];
        }
    }

    private void saveContents(UUID id, ItemStack[] contents) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(contents);
            out.flush();
            data.set("backpacks." + id, Base64.getEncoder().encodeToString(bytes.toByteArray()));
        } catch (IOException ex) { getLogger().severe("No se pudo guardar la mochila " + id + ": " + ex.getMessage()); }
    }

    private void persist() {
        try { data.save(dataFile); } catch (IOException ex) { getLogger().severe("No se pudo guardar backpacks.yml: " + ex.getMessage()); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("backpack.admin")) { sender.sendMessage(ChatColor.RED + "No tienes permiso."); return true; }
        if (args.length == 0) { sender.sendMessage(ChatColor.YELLOW + "/backpack give <jugador> <basic|iron|gold|diamond|netherite> [cantidad]"); sender.sendMessage(ChatColor.YELLOW + "/backpack module <jugador> [cantidad]"); return true; }
        if (args[0].equalsIgnoreCase("reload")) { reloadConfig(); sender.sendMessage(ChatColor.GREEN + "Configuración recargada."); return true; }
        if (args[0].equalsIgnoreCase("module") && args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(ChatColor.RED + "Jugador no encontrado."); return true; }
            int amount = args.length >= 3 ? Math.max(1, Math.min(64, parse(args[2], 1))) : 1;
            ItemStack module = createModule(); module.setAmount(amount); target.getInventory().addItem(module);
            sender.sendMessage(ChatColor.GREEN + "Módulo entregado a " + target.getName()); return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length >= 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(ChatColor.RED + "Jugador no encontrado."); return true; }
            int t = Arrays.asList(TIERS).indexOf(args[2].toLowerCase(Locale.ROOT));
            if (t < 0) { sender.sendMessage(ChatColor.RED + "Nivel inválido."); return true; }
            int amount = args.length >= 4 ? Math.max(1, Math.min(64, parse(args[3], 1))) : 1;
            for (int i=0;i<amount;i++) target.getInventory().addItem(createBackpack(t, UUID.randomUUID(), sender instanceof Player p ? p.getName() : "Servidor"));
            sender.sendMessage(ChatColor.GREEN + "Mochila(s) entregada(s) a " + target.getName()); return true;
        }
        sender.sendMessage(ChatColor.RED + "Uso incorrecto. Ejecuta /backpack."); return true;
    }
    private int parse(String s, int fallback) { try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return fallback; } }
}
