package com.manasluflux.addon.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.gui.UpdateScreen;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ManasluFlux update detector.
 *
 * <p>Every {@link #CHECK_INTERVAL_MS} it reads {@link #MANIFEST_URL} - a tiny JSON file in the
 * website repository that names the newest release and the jar that goes with it. When the jar
 * on disk is older, the update screen offers one button; pressing it downloads the new jar into
 * a temp folder, proves it really is a ManasluFlux jar of that exact version, then hands the
 * swap to a tiny relaunch script: the script waits for this JVM to exit, renames every old
 * manasluflux jar to {@code .disabled}, moves the new one into the mods folder and starts the
 * game again with the same java command line. If the button is never pressed the update is
 * forced {@link #FORCE_AFTER_MS} after it was first seen.
 *
 * <p>Nothing here runs on the render thread except opening the screen and stopping the game.
 */
public final class UpdateChecker {
    public static final UpdateChecker INSTANCE = new UpdateChecker();

    /** Small manifest kept in the website repository, next to the downloads folder. */
    public static final String MANIFEST_URL = "https://raw.githubusercontent.com/piolunson/ManasluFluxMeteorWebsite/main/update.json";

    /** How often the manifest is polled. */
    public static final long CHECK_INTERVAL_MS = 30_000L;

    /** How long an offered update may sit uninstalled before it is forced. */
    public static final long FORCE_AFTER_MS = 10 * 60_000L;

    private static final String MOD_ID = "manasluflux";
    private static final String JAR_PREFIX = "manasluflux-";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    public enum State {
        IDLE,
        CHECKING,
        UP_TO_DATE,
        AVAILABLE,
        DOWNLOADING,
        INSTALLING,
        FAILED
    }

    private final AtomicBoolean checking = new AtomicBoolean();
    private final AtomicBoolean installing = new AtomicBoolean();

    private volatile State state = State.IDLE;
    private volatile String installedVersion = "unknown";
    private volatile String latestVersion;
    private volatile String availableVersion;
    private volatile String downloadUrl;
    private volatile String error;
    private volatile int progress = -1;
    private volatile long availableSince;
    private volatile boolean promptPending;
    private volatile boolean notified;
    private volatile boolean pendingRestart;

    private Path installedJar;
    private boolean started;

    private UpdateChecker() {}

    public void init() {
        if (started) return;
        started = true;

        installedVersion = readInstalledVersion();
        installedJar = readInstalledJar();

        MeteorClient.EVENT_BUS.subscribe(this);

        Thread thread = new Thread(this::pollLoop, "ManasluFlux-Updater");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();

        AddonTemplate.LOG.info("ManasluFlux update detector started on v{}", installedVersion);
    }

    // ---------------------------------------------------------------- public state

    public State state() {
        return state;
    }

    public String installedVersion() {
        return installedVersion;
    }

    public String latestVersion() {
        return latestVersion != null ? latestVersion : installedVersion;
    }

    public String error() {
        return error;
    }

    public boolean hasUpdate() {
        return state == State.AVAILABLE || state == State.DOWNLOADING || state == State.INSTALLING;
    }

    /** Seconds left before the update is forced, or -1 when no update is waiting. */
    public long forceSecondsLeft() {
        if (state != State.AVAILABLE || availableSince == 0) return -1;
        long left = FORCE_AFTER_MS - (System.currentTimeMillis() - availableSince);
        return Math.max(0, left / 1000L);
    }

    /** One line for the update screen. */
    public String statusLine() {
        switch (state) {
            case IDLE:
                return "Waiting for the first check...";
            case CHECKING:
                return "Checking for updates...";
            case UP_TO_DATE:
                return pendingRestart
                    ? "v" + availableVersion + " is installed - restart Minecraft to load it."
                    : "You are on the latest version.";
            case AVAILABLE: {
                long left = forceSecondsLeft();
                return "Update available" + (left > 0 ? " - forcing in " + mmss(left) + " if you do not install it" : ".");
            }
            case DOWNLOADING:
                return progress >= 0 ? "Downloading the new jar... " + progress + "%" : "Downloading the new jar...";
            case INSTALLING:
                return "Installing and restarting Minecraft...";
            case FAILED:
                return "Update failed: " + (error == null ? "unknown error" : error);
            default:
                return "";
        }
    }

    public String buttonLabel() {
        switch (state) {
            case DOWNLOADING:
                return progress >= 0 ? "Downloading " + progress + "%" : "Downloading...";
            case INSTALLING:
                return "Restarting...";
            case FAILED:
                return "Retry update";
            case UP_TO_DATE:
                return pendingRestart ? "Installed" : "Up to date";
            default:
                return "Update & restart";
        }
    }

    // ---------------------------------------------------------------- checking

    public void checkNow() {
        if (!checking.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> {
            try {
                doCheck();
            } catch (Exception e) {
                fail(describe(e));
            } finally {
                checking.set(false);
            }
        }, "ManasluFlux-UpdateCheck");
        thread.setDaemon(true);
        thread.start();
    }

    private void pollLoop() {
        sleep(5_000L);

        while (true) {
            long started = System.currentTimeMillis();
            if (checking.compareAndSet(false, true)) {
                try {
                    doCheck();
                } catch (Exception e) {
                    fail(describe(e));
                } finally {
                    checking.set(false);
                }
            }
            sleep(Math.max(1_000L, CHECK_INTERVAL_MS - (System.currentTimeMillis() - started)));
        }
    }

    private void doCheck() throws IOException, InterruptedException {
        if (state == State.DOWNLOADING || state == State.INSTALLING) return;

        if (state != State.AVAILABLE) state = State.CHECKING;

        JsonObject json = fetchJson(MANIFEST_URL);
        String version = string(json, "version");
        String download = string(json, "download");

        if (version == null || download == null) {
            throw new IOException("update.json has no version/download");
        }

        latestVersion = version;
        downloadUrl = resolve(MANIFEST_URL, download);

        // Swapped in already, just waiting for the player to restart.
        if (pendingRestart) {
            state = State.UP_TO_DATE;
            return;
        }

        if (!Versions.isNewer(version, installedVersion)) {
            if (state == State.AVAILABLE || state == State.CHECKING) state = State.UP_TO_DATE;
            availableVersion = null;
            availableSince = 0;
            promptPending = false;
            notified = false;
            return;
        }

        if (!version.equals(availableVersion)) {
            availableVersion = version;
            availableSince = System.currentTimeMillis();
            promptPending = true;
            notified = false;
        }

        state = State.AVAILABLE;
        error = null;

        if (!notified) {
            notified = true;
            announce(version);
        }
    }

    private void announce(String version) {
        String prefix = commandPrefix();
        runOnClient(() -> ChatUtils.info(
            "ManasluFlux v%s is out (you are on v%s). Run %smfupdate or press the button in the window that just opened.",
            version, installedVersion, prefix
        ));
    }

    private void fail(String message) {
        error = message;
        // A failed re-check must not hide an update that is already offered or installing.
        if (state != State.AVAILABLE && state != State.DOWNLOADING && state != State.INSTALLING) state = State.FAILED;
        AddonTemplate.LOG.warn("ManasluFlux update check failed: {}", message);
    }

    // ---------------------------------------------------------------- installing

    /** The button. Returns false when there is nothing to install or an install is running. */
    public boolean startUpdate() {
        if (state != State.AVAILABLE && state != State.FAILED) return false;
        if (state == State.FAILED && availableVersion == null) return false;
        if (!installing.compareAndSet(false, true)) return false;

        state = State.DOWNLOADING;
        progress = -1;
        error = null;

        Thread thread = new Thread(this::downloadAndInstall, "ManasluFlux-Installer");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private void downloadAndInstall() {
        try {
            Path download = downloadNewJar();
            validate(download);
            state = State.INSTALLING;
            install(download);
        } catch (Exception e) {
            error = describe(e);
            state = State.FAILED;
            installing.set(false);
            AddonTemplate.LOG.warn("ManasluFlux update failed", e);
            runOnClient(() -> ChatUtils.error("ManasluFlux update failed: %s", error));
        }
    }

    private Path downloadNewJar() throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("manasluflux-update");
        dir.toFile().deleteOnExit();
        Path file = dir.resolve(jarName(availableVersion));

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", "ManasluFlux-Updater")
            .GET()
            .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("download returned HTTP " + response.statusCode());
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        long read = 0;
        int lastPercent = -1;

        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                read += n;
                if (total > 0) {
                    int percent = (int) Math.min(100, read * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        progress = percent;
                    }
                }
            }
        }

        progress = 100;
        if (Files.size(file) < 16 * 1024) throw new IOException("downloaded jar is only " + Files.size(file) + " bytes");
        return file;
    }

    /** A jar is only accepted if it is a real zip whose fabric.mod.json is this addon at that version. */
    private void validate(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) throw new IOException("downloaded file is not a mod jar");

            String text;
            try (InputStream in = zip.getInputStream(entry)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonObject meta = JsonParser.parseString(text).getAsJsonObject();
            String id = string(meta, "id");
            String version = string(meta, "version");

            if (!MOD_ID.equals(id)) throw new IOException("downloaded jar is '" + id + "', not ManasluFlux");
            if (!availableVersion.equals(version)) {
                throw new IOException("downloaded jar is v" + version + ", expected v" + availableVersion);
            }
        } catch (RuntimeException e) {
            throw new IOException("downloaded jar has a broken fabric.mod.json", e);
        }
    }

    private void install(Path download) throws IOException, InterruptedException {
        Path currentJar = installedJar;

        if (currentJar == null || !Files.isRegularFile(currentJar)) {
            throw new IOException("this build runs from a folder, not a jar - install the new jar by hand");
        }

        Path modsDir = currentJar.getParent();
        Path target = modsDir.resolve(jarName(availableVersion));

        // Every enabled manasluflux jar in the folder gets disabled, so the new one is the only
        // version Fabric can find on the next start.
        List<Path> old = new ArrayList<>();
        try (Stream<Path> files = Files.list(modsDir)) {
            files.filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.startsWith(JAR_PREFIX) && name.endsWith(".jar");
                })
                .filter(p -> !p.getFileName().toString().equals(target.getFileName().toString()))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(old::add);
        }

        if (!old.contains(currentJar)) old.add(currentJar);

        RelaunchScript.Launch launch = resolveLaunch();

        Path script = RelaunchScript.write(download, target, old, launch,
            ProcessHandle.current().pid(), System.getProperty("user.dir", "."));
        Path log = script.resolveSibling("manasluflux-update.log");

        ProcessBuilder builder = isWindows()
            ? new ProcessBuilder("cmd.exe", "/c", script.toString())
            : new ProcessBuilder("/bin/sh", script.toString());

        builder.directory(new File(System.getProperty("user.dir", ".")));
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        builder.start();

        if (launch == null) {
            AddonTemplate.LOG.info("ManasluFlux {} installed - waiting for a manual restart", availableVersion);
            runOnClient(() -> ChatUtils.info(
                "ManasluFlux v%s downloaded and installed, v%s disabled. Start Minecraft again to load it.",
                availableVersion, installedVersion
            ));
            pendingRestart = true;
            state = State.UP_TO_DATE;
            installing.set(false);
            return;
        }

        AddonTemplate.LOG.info("ManasluFlux {} downloaded - restarting to load it", availableVersion);

        runOnClient(() -> {
            ChatUtils.info("ManasluFlux v%s downloaded. Disabling v%s and restarting...", availableVersion, installedVersion);
            MinecraftClient client = MinecraftClient.getInstance();
            client.scheduleStop();
        });
    }

    /**
     * How to start the game again. ProcessHandle only reports arguments on some platforms, so on
     * Windows the original command line is read back from WMI - that keeps the exact quoting of
     * paths with spaces. Null means the swap happens but the game is left to the player.
     */
    private RelaunchScript.Launch resolveLaunch() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        String command = info.command().orElse(null);
        String[] arguments = info.arguments().orElse(null);

        if (command != null && !command.isBlank() && arguments != null && arguments.length > 0) {
            return RelaunchScript.Launch.argv(command, arguments);
        }

        if (isWindows()) {
            String raw = windowsCommandLine();
            if (raw != null && !raw.isBlank()) return RelaunchScript.Launch.raw(raw.trim());
        }

        AddonTemplate.LOG.warn("ManasluFlux could not read its launch command; the update will be installed without restarting");
        return null;
    }

    private static String windowsCommandLine() {
        String script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
            + "[Console]::Out.Write((Get-CimInstance Win32_Process -Filter 'ProcessId=" + ProcessHandle.current().pid() + "').CommandLine)";

        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start();

            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }

            return process.exitValue() == 0 ? output : null;
        } catch (Exception e) {
            AddonTemplate.LOG.warn("Could not read the Windows command line", e);
            return null;
        }
    }

    // ---------------------------------------------------------------- tick handling

    @EventHandler
    private void onTick(TickEvent.Post event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (state == State.AVAILABLE && promptPending && client.currentScreen == null) {
            promptPending = false;
            client.setScreen(new UpdateScreen());
        }

        if (state == State.AVAILABLE && !installing.get()) {
            if (forceSecondsLeft() == 0) {
                ChatUtils.warning("ManasluFlux v%s was not installed in time - updating and restarting now.", availableVersion);
                startUpdate();
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String readInstalledVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    private static Path readInstalledJar() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
            .flatMap(c -> c.getOrigin().getPaths().stream().filter(Files::isRegularFile).findFirst())
            .orElse(null);
    }

    private static JsonObject fetchJson(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(HTTP_TIMEOUT)
            .header("User-Agent", "ManasluFlux-Updater")
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("update server returned HTTP " + response.statusCode());

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    private static String resolve(String base, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        return URI.create(base).resolve(path).toString();
    }

    private static String jarName(String version) {
        return JAR_PREFIX + version + ".jar";
    }

    private static String commandPrefix() {
        try {
            return meteordevelopment.meteorclient.systems.config.Config.get().prefix.get();
        } catch (Throwable ignored) {
            return ".";
        }
    }

    private static void runOnClient(Runnable action) {
        MinecraftClient.getInstance().execute(action);
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) message = e.getClass().getSimpleName();
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }

    private static String mmss(long seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
