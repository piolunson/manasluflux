package com.manasluflux.addon;

import com.manasluflux.addon.commands.AddTextCommand;
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
import com.manasluflux.addon.hud.PlayerListHud;
import com.manasluflux.addon.modules.AddText;
import com.manasluflux.addon.modules.AutoLogin;
import com.manasluflux.addon.modules.AutoTotem;
import com.manasluflux.addon.modules.BoatFlight;
import com.manasluflux.addon.modules.AutoEat;
import com.manasluflux.addon.modules.AutoFish;
import com.manasluflux.addon.modules.AutoRespawn;
import com.manasluflux.addon.modules.AutoResponder;
import com.manasluflux.addon.modules.AutoWalk;
import com.manasluflux.addon.modules.AutoLog;
import com.manasluflux.addon.modules.BlockReplacer;
import com.manasluflux.addon.modules.ChatLogger;
import com.manasluflux.addon.modules.Clicker;
import com.manasluflux.addon.modules.ClickTp;
import com.manasluflux.addon.modules.ClientSideNightVision;
import com.manasluflux.addon.modules.CrystalAura;
import com.manasluflux.addon.modules.DeathCoords;
import com.manasluflux.addon.modules.ElytraFlight;
import com.manasluflux.addon.modules.InstantTnt;
import com.manasluflux.addon.modules.ModuleExample;
import com.manasluflux.addon.modules.PacketLimiter;
import com.manasluflux.addon.modules.SoundBlocker;
import com.manasluflux.addon.modules.TabCompletePrivacy;
import com.manasluflux.addon.modules.TabLogger;
import com.manasluflux.addon.modules.WhitelistFastUse;
import com.manasluflux.addon.modules.Mute;
import com.manasluflux.addon.modules.Path;
import com.manasluflux.addon.modules.PearlPhase;
import com.manasluflux.addon.modules.SilentAura;
import com.manasluflux.addon.modules.TntPlacer;
import com.manasluflux.addon.modules.ToggleTab;
import com.manasluflux.addon.modules.UniversalColoredChat;
import com.manasluflux.addon.modules.UniversalFlight;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Manaslu Flux", () -> new ItemStack(Items.NETHER_STAR));
    public static final HudGroup HUD_GROUP = new HudGroup("Manaslu Flux");
    public static final Category CLIENT_SIDE_CATEGORY = new Category("Manaslu Flux Client Side", () -> new ItemStack(Items.OAK_SIGN));
    public static final Category COMBAT_CATEGORY = new Category("Manaslu Flux Combat", () -> new ItemStack(Items.END_CRYSTAL));
    public static final Category REWRITE_CATEGORY = new Category("Manaslu Flux Rewrite", () -> new ItemStack(Items.ENCHANTED_BOOK));

    @Override
    public void onInitialize() {
        LOG.info("Initializing ManasluFlux by piolunson & various ai providers");

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

        // Rewrite category (Meteor built-ins, rewritten for ManasluFlux)
        Modules.get().add(new AutoRespawn());
        Modules.get().add(new AutoResponder());
        Modules.get().add(new DeathCommands());
        Modules.get().add(new ChatLogger());
        Modules.get().add(new TabLogger());
        Modules.get().add(new TabCompletePrivacy());
        Modules.get().add(new ClickTp());
        Modules.get().add(new Clicker());
        Modules.get().add(new SoundBlocker());
        Modules.get().add(new PacketLimiter());

        // Client-side modules (own ClickGUI category)
        Modules.get().add(new ClientSideNightVision());
        Modules.get().add(new ToggleTab());
        Modules.get().add(new InstantTnt());
        Modules.get().add(new UniversalColoredChat());
        Modules.get().add(new DeathCoords());
        Modules.get().add(new WhitelistFastUse());
        Modules.get().add(new AddText());
        Modules.get().add(new AutoLogin());

        // Combat modules (own ClickGUI category)
        Modules.get().add(new TntPlacer());
        Modules.get().add(new SilentAura());
        Modules.get().add(new CrystalAura());
        Modules.get().add(new AutoTotem());

        // Example module & HUD kept from the template
        Modules.get().add(new ModuleExample());
        Hud.get().register(HudExample.INFO);
        Hud.get().register(ImageHud.INFO);
        Hud.get().register(GifHud.INFO);
        Hud.get().register(PlayerListHud.INFO);

        // Commands (20)
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
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(CLIENT_SIDE_CATEGORY);
        Modules.registerCategory(COMBAT_CATEGORY);
        Modules.registerCategory(REWRITE_CATEGORY);
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
