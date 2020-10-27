package me.mrletsplay.minebay;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import me.mrletsplay.minebay.utils.ItemUtils;
import me.mrletsplay.mrcore.misc.Complex;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.common.io.Files;

import me.mrletsplay.mrcore.bukkitimpl.config.BukkitCustomConfig;
import me.mrletsplay.mrcore.config.ConfigLoader;
import me.mrletsplay.mrcore.misc.StringUtils;
import org.yaml.snakeyaml.Yaml;

public class AuctionRoom {

	private String owner;
	private int taxshare;
	private int slots;
	private int roomID;
	private String name, description;
	private ItemStack icon;
	private boolean isDefaultRoom, isPrivateRoom;
	private List<String> playerList;
	private List<ItemStack> whiteList;
	
	private File roomFile;
	private BukkitCustomConfig roomConfig;
	
	@SuppressWarnings("deprecation")
	public AuctionRoom(int id) {
		this.roomID = id;
		roomFile = new File(Main.pl.getDataFolder(), "AuctionRooms/" + id + ".yml");
		roomConfig = ConfigLoader.loadConfigFromFile(new BukkitCustomConfig(roomFile), roomFile, true);
		this.owner = roomConfig.getString("owner");
		boolean s = false;
		if(this.owner!=null) {
			if(Utils.isUUID(this.owner)) {
				if(!Config.useUUIDs) {
					Main.pl.getLogger().info("Converting room "+id+"'s owner uuid to name...");
					this.owner = Bukkit.getPlayer(UUID.fromString(owner)).getName();
					s = true;
				}
			}else if(Config.useUUIDs) {
				Main.pl.getLogger().info("Converting room "+id+"'s owner name to uuid...");
				owner = Bukkit.getOfflinePlayer(owner).getUniqueId().toString();
				s = true;
			}
		}
		this.taxshare = roomConfig.getInt("tax-share");
		this.slots = roomConfig.getInt("slots");
		this.name = roomConfig.getString("name");
		this.description = roomConfig.getString("description");
		this.icon = roomConfig.getItemStack("icon");
		this.isDefaultRoom = roomConfig.getBoolean("default-room");
		this.isPrivateRoom = roomConfig.getBoolean("private-room");
		this.playerList = roomConfig.getStringList("player-list", new ArrayList<>(), false);
		File whitelistFile = new File(this.roomFile.getParent() + "/" + this.roomID + "-whitelist.yml");
		if (!whitelistFile.exists()) {
			try {
				whitelistFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		FileConfiguration whitelist = YamlConfiguration.loadConfiguration(whitelistFile);
		this.whiteList = (List<ItemStack>) whitelist.get("whitelist", this.whiteList);
//		this.whiteList = ((List<HashMap<String, Object>>) roomConfig.toMap().get("whitelist")).stream().map(map -> {
//			try {
//				return ItemUtils.deserialize(map);
//			} catch (IllegalAccessException e) {
//				e.printStackTrace();
//			} catch (InvocationTargetException e) {
//				e.printStackTrace();
//			}
//			return null;
//		}).collect(Collectors.toList());
		this.whiteList.forEach(item -> System.out.println(item.hasItemMeta()));
		System.out.println(this.whiteList.size());
		if(s) saveAllSettings();
	}
	
	public void setDefaultSettings(String owner, boolean isDefaultRoom){
		this.owner = owner;
		this.taxshare = Config.config.getInt("minebay.user-rooms.default-tax-percent");
		this.slots = Config.config.getInt("minebay.user-rooms.default-slot-number");
		this.icon = new ItemStack(Material.getMaterial(Config.config.getString("minebay.user-rooms.default-icon-material")));
		this.isDefaultRoom = isDefaultRoom;
		this.isPrivateRoom = false;
		if(owner!=null){
			this.name = Config.config.getString("minebay.user-rooms.default-name").replace("%player%", getOwnerName());
		}else{
			this.name = "Default Auction Room";
		}
		this.description = null;
		this.playerList = new ArrayList<>();
		saveAllSettings();
	}
	
	public void saveAllSettings(){
		roomConfig.set("owner", owner);
		roomConfig.set("tax-share", taxshare);
		roomConfig.set("slots", slots);
		roomConfig.set("name", name);
		roomConfig.set("description", description);
		roomConfig.set("icon", icon);
		roomConfig.set("default-room", isDefaultRoom);
		roomConfig.set("private-room", isPrivateRoom);
		roomConfig.set("player-list", playerList);
//		roomConfig.set("whitelist", this.whiteList.stream().map(ItemStack::serialize).map(stringObjectMap -> stringObjectMap.entrySet().stream().map(stringObjectEntry -> {
//			if(stringObjectEntry.getValue() instanceof ConfigurationSerializable) {
//				stringObjectEntry.setValue(((ConfigurationSerializable) stringObjectEntry.getValue()).serialize());
//			}
//			return stringObjectEntry;
//		}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (prev, next) -> next, HashMap::new))).collect(Collectors.toList()));
		File whitelistFile = new File(this.roomFile.getParent() + "/" + this.roomID + "-whitelist.yml");
		if (!whitelistFile.exists()) {
			try {
				whitelistFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		FileConfiguration whitelist = YamlConfiguration.loadConfiguration(whitelistFile);
		whitelist.set("whitelist", this.whiteList);
		try {
			whitelist.save(whitelistFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
//		roomConfig.set("whitelist", this.whiteList.stream().map(ItemUtils::serialize).collect(Collectors.toList()));
		saveRoomConfig();
	}
	
	public void setName(String name){
		this.name = name;
	}
	
	public void saveRoomConfig(){
		roomConfig.saveToFile();
	}
	
	public String getOwner() {
		return owner;
	}
	
	public boolean isOwner(Player p) {
		if(Config.useUUIDs) {
			return p.getUniqueId().toString().equals(owner);
		}else {
			return p.getName().equals(owner);
		}
	}
	
	public boolean canEdit(Player p) {
		return (p!=null && isOwner(p)) || (p!=null && isDefaultRoom && p.hasPermission("minebay.default-rooms.allow-edit")) || (p!=null && !isDefaultRoom && p.hasPermission("minebay.user-rooms.allow-edit"));
	}
	
	public String getOwnerName() {
		if(owner==null) return null;
		if(!Config.useUUIDs) {
			return owner;
		}else {
			return Bukkit.getOfflinePlayer(UUID.fromString(owner)).getName();
		}
	}
	
	public int getTaxshare() {
		return taxshare;
	}
	
	public int getSlots() {
		return slots;
	}
	
	public int getID() {
		return roomID;
	}
	
	public String getName() {
		return name;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setSlots(int slots) {
		this.slots = slots;
	}
	
	public void setTaxshare(int taxshare) {
		this.taxshare = taxshare;
	}
	
	public void setIcon(ItemStack icon) {
		this.icon = icon;
	}
	
	public ItemStack getIcon() {
		return icon;
	}
	
	public void addPlayerToList(OfflinePlayer player) {
		playerList.add(Config.useUUIDs ? player.getUniqueId().toString() : player.getName());
	}
	
	public boolean isPlayerOnList(OfflinePlayer player) {
		return playerList.contains(Config.useUUIDs ? player.getUniqueId().toString() : player.getName());
	}
	
	public void removePlayerFromList(OfflinePlayer player) {
		playerList.remove(Config.useUUIDs ? player.getUniqueId().toString() : player.getName());
	}
	
	@SuppressWarnings("deprecation")
	public List<OfflinePlayer> getPlayerList() {
		return playerList.stream()
				.map(p -> Config.useUUIDs ? Bukkit.getOfflinePlayer(UUID.fromString(p)) : Bukkit.getOfflinePlayer(p))
				.collect(Collectors.toList());
	}
	
	public boolean canSell(Player p) {
		return isOwner(p) || (isPrivateRoom == playerList.contains(Config.useUUIDs ? p.getUniqueId().toString() : p.getName()));
	}
	
	public void addSellItem(SellItem item){
		List<Integer> ids = getItemIDs();
		int iID = getNewItemID();
		ids.add(iID);
		roomConfig.set("sold-items.ids", ids);
		roomConfig.set("sold-items.item."+iID+".item", item.getItem());
		roomConfig.set("sold-items.item."+iID+".seller", item.getSeller());
		roomConfig.set("sold-items.item."+iID+".price", item.getPrice().toString());
		saveRoomConfig();
		updateMineBay();
	}
	
	public void removeSellItem(int id){
		List<Integer> ids = getItemIDs();
		if(ids.contains((Integer)id)){
			ids.remove((Integer)id);
			roomConfig.set("sold-items.ids", ids);
			roomConfig.unset("sold-items.item."+id);
			saveRoomConfig();
		}
		updateMineBay();
	}
	
	public List<SellItem> getSoldItems(){
		List<Integer> ids = getItemIDs();
		List<SellItem> items = new ArrayList<>();
		for(int id : ids){
			items.add(getItemByID(id));
		}
		return items;
	}
	
	public int getNewItemID(){
		int id = 0;
		List<Integer> ids = getItemIDs();
		while(ids.contains(id)){
			id++;
		}
		return id;
	}
	
	public List<Integer> getItemIDs(){
		return roomConfig.getIntegerList("sold-items.ids");
	}
	
	public int getOccupiedSlots(){
		List<String> sellers = new ArrayList<>();
		for(SellItem i : getSoldItems()){
			if(!sellers.contains(i.getSeller())){
				sellers.add(i.getSeller());
			}
		}
		return sellers.size();
	}
	
	public List<SellItem> getSoldItemsBySeller(Player seller){
		List<SellItem> it = new ArrayList<>();
		for(SellItem i : getSoldItems()){
			if(i.isSeller(seller)){
				it.add(i);
			}
		}
		return it;
	}
	
	public SellItem getItemByID(int id){
		if(getItemIDs().contains(id)){
			ItemStack item = roomConfig.getItemStack("sold-items.item."+id+".item");
			String seller = roomConfig.getString("sold-items.item."+id+".seller");
			BigDecimal price = new BigDecimal(roomConfig.getString("sold-items.item."+id+".price"));
			return new SellItem(item, AuctionRooms.getAuctionRoomByID(roomID), seller, price, id);
		}else{
			return null;
		}
	}
	
	public Inventory getMineBayInv(int page, Player p) {
		return GUIs.getAuctionRoomGUI(p, roomID, page);
	}
	
	public void updateMineBay(){
		GUIs.AUCTION_ROOM_GUI.refreshAllInstances(holder -> (int) holder.getProperty(Main.pl, "room_id") == roomID);
	}
	
	public void updateSettings(){
		GUIs.AUCTION_ROOM_SETTINGS_GUI.refreshAllInstances(holder -> (int) holder.getProperty(Main.pl, "room_id") == roomID);
	}
	
	public void updatePlayerList(){
		GUIs.AUCTION_ROOM_PLAYER_LIST_GUI.refreshAllInstances(holder -> (int) holder.getProperty(Main.pl, "room_id") == roomID);
	}
	
	public ItemStack getSelectItemStack(Player p){
		if(icon==null) return null;
		ItemStack newItem = icon.clone();
		ItemMeta im = newItem.getItemMeta();
		im.setDisplayName(Config.getMessage("minebay.gui.rooms.room-item.name", "room-name", name, "room-id", ""+roomID));
		List<String> lore = Config.getMessageList("minebay.gui.rooms.room-item.lore",
				"owner", owner!=null?getOwnerName():Config.getMessage("minebay.gui.misc.none"),
				"slots-limit", (slots==-1?Config.getMessage("minebay.gui.rooms.room-item.slots-unlimited"):""+slots),
				"slots-occupied", ""+getOccupiedSlots(),
				"tax", ""+taxshare,
				"room-id", ""+roomID,
				"can-edit", canEdit(p) ? Config.getMessage("minebay.gui.rooms.room-item.can-edit"):"",
				"is-private", isPrivateRoom ? (canSell(p) ? Config.getMessage("minebay.gui.rooms.room-item.is-private-permission") : Config.getMessage("minebay.gui.rooms.room-item.is-private-no-permission")):"",
				"is-banned", !isPrivateRoom && !canSell(p) ? Config.getMessage("minebay.gui.rooms.room-item.is-banned") : "");
		List<String> fLore = new ArrayList<>();
		for(String s2 : lore) {
			if(!s2.contains("%description%")) {
				if(!s2.isEmpty()) fLore.add(s2);
				continue;
			}
			if(description!=null) {
				String fS = s2.replace("%description%", description);
				StringUtils.wrapString(fS, 50).stream()
						.map(s -> Config.getMessage("minebay.gui.rooms.room-item.description-linebreak-color")+s)
						.forEach(fLore::add);
			}else {
				fLore.add(s2.replace("%description%", Config.getMessage("minebay.gui.misc.none")));
			}
		}
		im.setLore(fLore);
		im.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS);
		newItem.setItemMeta(im);
		return newItem;
	}
	
	public int getWorth(){
		if(isDefaultRoom){
			return 0;
		}else{
			int sl = (slots - Config.config.getInt("minebay.user-rooms.default-slot-number")) * Config.config.getInt("minebay.user-rooms.slot-sell-price");
			int pr = Config.config.getInt("minebay.user-rooms.room-sell-price");
			return sl + pr;
		}
	}
	
	public boolean isDefaultRoom() {
		return isDefaultRoom;
	}
	
	public void setPrivateRoom(boolean isPrivateRoom) {
		this.isPrivateRoom = isPrivateRoom;
	}
	
	public boolean isPrivateRoom() {
		return isPrivateRoom;
	}
	
	public boolean backupConfig(File to) {
		try {
			if(!roomFile.exists()) return false;
			if(!to.exists()) {
				to.getParentFile().mkdirs();
				to.createNewFile();
			}
			Files.copy(roomFile, to);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public List<ItemStack> getWhiteList() {
		return whiteList;
	}
}
