package com.manasluflux.addon;

import com.manasluflux.addon.commands.AddTextCommand;
import com.manasluflux.addon.gui.NotepadTab;
import com.manasluflux.addon.gui.ToDoTab;
import com.manasluflux.addon.commands.AutoEatCommand;
import com.manasluflux.addon.commands.AutoFishCommand;
import com.manasluflux.addon.commands.AutoLogCommand;
import com.manasluflux.addon.commands.CoordsCommand;
import com.manasluflux.addon.commands.DayCommand;
import com.manasluflux.addon.commands.DurabilityCommand;
import com.manasluflux.addon.commands.EffectsCommand;
import com.manasluflux.addon.commands.EnchantCommand;
import com.manasluflux.addon.commands.FpsCommand;
import com.manasluflux.addon.commands.HealCommand;
import com.manasluflux.addon.commands.JavaScriptCommand;
import com.manasluflux.addon.commands.KillCommand;
import com.manasluflux.addon.commands.MfFakePlayerCommand;
import com.manasluflux.addon.commands.MfHelpCommand;
import com.manasluflux.addon.commands.MfWebSpeedCommand;
import com.manasluflux.addon.commands.VisualizeWebBypassGraphCommand;
import com.manasluflux.addon.commands.MuteCommand;
import com.manasluflux.addon.commands.PathCommand;
import com.manasluflux.addon.commands.PingCommand;
import com.manasluflux.addon.commands.RenamerCommand;
import com.manasluflux.addon.commands.ServerInfoCommand;
import com.manasluflux.addon.commands.SkinCommand;
import com.manasluflux.addon.commands.SmCommand;
import com.manasluflux.addon.commands.StatCommand;
import com.manasluflux.addon.commands.TrashCommand;
import com.manasluflux.addon.commands.UuidCommand;
import com.manasluflux.addon.commands.WaypointCommand;
import com.manasluflux.addon.hud.GifHud;
import com.manasluflux.addon.hud.HudExample;
import com.manasluflux.addon.hud.ImageHud;
import com.manasluflux.addon.hud.KillCounterHud;
import com.manasluflux.addon.hud.PlayerListHud;
import com.manasluflux.addon.hud.ServerInfoHud;
import com.manasluflux.addon.hud.SessionInfoHud;
import com.manasluflux.addon.modules.AutoLogin;
import com.manasluflux.addon.modules.AutoTotem;
import com.manasluflux.addon.modules.BedAura;
import com.manasluflux.addon.modules.BoatFlight;
import com.manasluflux.addon.modules.AutoChatClear;
import com.manasluflux.addon.modules.AutoEat;
import com.manasluflux.addon.modules.AntiDropLagBack;
import com.manasluflux.addon.modules.AutoFish;
import com.manasluflux.addon.modules.AutoRespawn;
import com.manasluflux.addon.modules.AutoResponder;
import com.manasluflux.addon.modules.AutoWalk;
import com.manasluflux.addon.modules.AutoLog;
import com.manasluflux.addon.modules.AutoMapPart;
import com.manasluflux.addon.modules.AutoWither;
import com.manasluflux.addon.modules.BlockReplacer;
import com.manasluflux.addon.modules.ChatLogger;
import com.manasluflux.addon.modules.BritishChat;
import com.manasluflux.addon.modules.Clicker;
import com.manasluflux.addon.modules.ClickTp;
import com.manasluflux.addon.modules.ClientBlockEntities;
import com.manasluflux.addon.modules.ClientSideNightVision;
import com.manasluflux.addon.modules.CobwebBypass;
import com.manasluflux.addon.modules.CrystalAura;
import com.manasluflux.addon.modules.DeathCoords;
import com.manasluflux.addon.modules.DiscordRpc;
import com.manasluflux.addon.modules.ElytraFlight;
import com.manasluflux.addon.modules.InstantTnt;
import com.manasluflux.addon.modules.InventoryKeeper;
import com.manasluflux.addon.modules.ModuleExample;
import com.manasluflux.addon.modules.PacketLimiter;
import com.manasluflux.addon.modules.SoundBlocker;
import com.manasluflux.addon.modules.TabCompletePrivacy;
import com.manasluflux.addon.modules.TabLogger;
import com.manasluflux.addon.modules.WhitelistFastUse;
import com.manasluflux.addon.modules.Mute;
import com.manasluflux.addon.modules.Path;
import com.manasluflux.addon.modules.PearlPhase;
import com.manasluflux.addon.modules.PingSpoofer;
import com.manasluflux.addon.modules.PlaceTravel;
import com.manasluflux.addon.modules.Placer;
import com.manasluflux.addon.modules.SilentAura;
import com.manasluflux.addon.modules.TntPlacer;
import com.manasluflux.addon.modules.ToggleTab;
import com.manasluflux.addon.modules.UniversalColoredChat;
import com.manasluflux.addon.modules.UniversalFlight;
import com.manasluflux.addon.modules.VClipPhaseFill;
import com.manasluflux.addon.modules.Waypoint;
import com.manasluflux.addon.modules.DeathCommands;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    // Icons: plain ItemStacks are safe in yarn 1.21.11 (components bind lazily there).
    public static final HudGroup HUD_GROUP = new HudGroup("Manaslu Flux");

    // Modules are grouped by what they actually do: fighting, getting around, keeping you alive,
    // changing the world, changing what you see, the rest (chat, logs, tools), and the ones whose
    // job is beating a server/anticheat check rather than changing what you do.
    public static final Category COMBAT_CATEGORY = new Category("Manaslu Flux Combat", new ItemStack(Items.END_CRYSTAL));
    public static final Category MOVEMENT_CATEGORY = new Category("Manaslu Flux Movement", new ItemStack(Items.FEATHER));
    public static final Category PLAYER_CATEGORY = new Category("Manaslu Flux Player", new ItemStack(Items.GOLDEN_APPLE));
    public static final Category WORLD_CATEGORY = new Category("Manaslu Flux World", new ItemStack(Items.GRASS_BLOCK));
    public static final Category CLIENT_SIDE_CATEGORY = new Category("Manaslu Flux Client Side", new ItemStack(Items.OAK_SIGN));
    public static final Category UTILITY_CATEGORY = new Category("Manaslu Flux Utility", new ItemStack(Items.COMPASS));
    public static final Category BYPASS_CATEGORY = new Category("Manaslu Flux Bypasses", new ItemStack(Items.ENDER_PEARL));

    @Override
    public void onInitialize() {
        LOG.info("Initializing ManasluFlux by piolunson & various ai providers");

        // GuiNotes backing the Notepad / To Do top-bar tabs (loaded from disk on first screen open)
        meteordevelopment.meteorclient.gui.tabs.Tabs.add(new NotepadTab());
        meteordevelopment.meteorclient.gui.tabs.Tabs.add(new ToDoTab());

        // Modules (backing some of the commands)
        Modules.get().add(new Waypoint());
        Modules.get().add(new Path());
        Modules.get().add(new PearlPhase());
        Modules.get().add(new AutoLog());
        Modules.get().add(new AutoFish());
        Modules.get().add(new AutoEat());
        Modules.get().add(new AutoWalk());
        Modules.get().add(new Mute());
        Modules.get().add(new UniversalFlight());
        Modules.get().add(new BoatFlight());
        Modules.get().add(new ElytraFlight());
        Modules.get().add(new BlockReplacer());
        Modules.get().add(new AutoMapPart());
        Modules.get().add(new AutoWither());
        Modules.get().add(new Placer());
        Modules.get().add(new PingSpoofer());
        Modules.get().add(new InventoryKeeper());
        Modules.get().add(new PlaceTravel());
        Modules.get().add(new VClipPhaseFill());
        Modules.get().add(new CobwebBypass());

        // Rewrite category (Meteor built-ins, rewritten for ManasluFlux)
        Modules.get().add(new AutoRespawn());
        Modules.get().add(new AutoResponder());
        Modules.get().add(new DeathCommands());
        Modules.get().add(new AutoChatClear());
        Modules.get().add(new ChatLogger());
        Modules.get().add(new BritishChat());
        Modules.get().add(new TabLogger());
        Modules.get().add(new TabCompletePrivacy());
        Modules.get().add(new ClickTp());
        Modules.get().add(new Clicker());
        Modules.get().add(new SoundBlocker());
        Modules.get().add(new PacketLimiter());

        // Client-side modules (own ClickGUI category)
        Modules.get().add(new ClientBlockEntities());
        Modules.get().add(new AntiDropLagBack());
        Modules.get().add(new ClientSideNightVision());
        Modules.get().add(new ToggleTab());
        Modules.get().add(new InstantTnt());
        Modules.get().add(new UniversalColoredChat());
        Modules.get().add(new DeathCoords());
        Modules.get().add(new DiscordRpc());
        Modules.get().add(new WhitelistFastUse());
        Modules.get().add(new AutoLogin());

        // Combat modules (own ClickGUI category)
        Modules.get().add(new TntPlacer());
        Modules.get().add(new SilentAura());
        Modules.get().add(new CrystalAura());
        Modules.get().add(new AutoTotem());
        Modules.get().add(new BedAura());

        // Example module & HUD kept from the template
        Modules.get().add(new ModuleExample());
        Hud.get().register(HudExample.INFO);
        Hud.get().register(ImageHud.INFO);
        Hud.get().register(GifHud.INFO);
        Hud.get().register(PlayerListHud.INFO);
        Hud.get().register(ServerInfoHud.INFO);
        Hud.get().register(SessionInfoHud.INFO);
        Hud.get().register(KillCounterHud.INFO);

        // Commands (28)
        Commands.add(new PingCommand());
        Commands.add(new CoordsCommand());
        Commands.add(new DayCommand());
        Commands.add(new ServerInfoCommand());
        Commands.add(new FpsCommand());
        Commands.add(new HealCommand());
        Commands.add(new EffectsCommand());
        Commands.add(new EnchantCommand());
        Commands.add(new DurabilityCommand());
        Commands.add(new TrashCommand());
        Commands.add(new RenamerCommand());
        Commands.add(new SkinCommand());
        Commands.add(new UuidCommand());
        Commands.add(new StatCommand());
        Commands.add(new WaypointCommand());
        Commands.add(new PathCommand());
        Commands.add(new AutoLogCommand());
        Commands.add(new AutoFishCommand());
        Commands.add(new AutoEatCommand());
        Commands.add(new MuteCommand());
        Commands.add(new SmCommand());
        Commands.add(new JavaScriptCommand());
        Commands.add(new AddTextCommand());
        Commands.add(new KillCommand());
        Commands.add(new MfHelpCommand());
        Commands.add(new MfFakePlayerCommand());
        Commands.add(new MfWebSpeedCommand());
        Commands.add(new VisualizeWebBypassGraphCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(COMBAT_CATEGORY);
        Modules.registerCategory(MOVEMENT_CATEGORY);
        Modules.registerCategory(PLAYER_CATEGORY);
        Modules.registerCategory(WORLD_CATEGORY);
        Modules.registerCategory(CLIENT_SIDE_CATEGORY);
        Modules.registerCategory(UTILITY_CATEGORY);
        Modules.registerCategory(BYPASS_CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.manasluflux.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("piolunson", "manasluflux");
    }
}
