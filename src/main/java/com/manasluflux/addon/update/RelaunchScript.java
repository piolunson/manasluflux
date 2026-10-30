package com.manasluflux.addon.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Writes the little script that finishes an update after Minecraft has exited.
 *
 * <p>The game cannot replace the jar it is running from, so the script does the swap instead:
 * it polls the game's PID until it is gone, renames every old manasluflux jar to
 * {@code .disabled} (Fabric ignores that suffix), moves the downloaded jar into the mods folder
 * and - when the launch command is known - starts the same java command line again.
 *
 * <p>Deliberately free of game classes so it can be generated and inspected on its own.
 */
final class RelaunchScript {
    private RelaunchScript() {}

    /**
     * How to start the game again. Windows keeps the original command line as one string (that
     * is the only form that survives paths with spaces); Posix gets the program plus arguments.
     * A null Launch means "swap the jars, do not start anything".
     */
    static final class Launch {
        private final String raw;
        private final String program;
        private final String[] arguments;

        private Launch(String raw, String program, String[] arguments) {
            this.raw = raw;
            this.program = program;
            this.arguments = arguments;
        }

        static Launch raw(String commandLine) {
            return new Launch(commandLine, null, null);
        }

        static Launch argv(String program, String[] arguments) {
            return new Launch(null, program, arguments);
        }
    }

    static Path write(Path download, Path target, List<Path> disable, Launch launch, long pid, String workingDir) throws IOException {
        Path dir = Files.createTempDirectory("manasluflux-relaunch");

        if (isWindows()) {
            Path script = dir.resolve("manasluflux-restart.bat");
            Files.writeString(script, batch(download, target, disable, launch, pid, workingDir), StandardCharsets.US_ASCII);
            return script;
        }

        Path script = dir.resolve("manasluflux-restart.sh");
        Files.writeString(script, shell(download, target, disable, launch, pid, workingDir), StandardCharsets.UTF_8);
        if (!script.toFile().setExecutable(true)) {
            throw new IOException("could not make " + script + " executable");
        }
        return script;
    }

    private static String batch(Path download, Path target, List<Path> disable, Launch launch, long pid, String workingDir) {
        StringBuilder bat = new StringBuilder();

        bat.append("@echo off\r\n");
        bat.append("setlocal\r\n");
        bat.append("cd /d \"").append(quote(workingDir)).append("\"\r\n");

        // Wait for the game to exit. findstr is called by its full path: a "find" on PATH can be
        // Git's MSYS find, which fails here and would run the swap under the running game.
        bat.append(":mfwait\r\n");
        bat.append("tasklist /FI \"PID eq ").append(pid).append("\" 2>nul | \"%SystemRoot%\\System32\\findstr.exe\" /C:\"")
            .append(pid).append("\" >nul\r\n");
        bat.append("if not errorlevel 1 (\r\n");
        bat.append("  ping -n 2 127.0.0.1 >nul\r\n");
        bat.append("  goto mfwait\r\n");
        bat.append(")\r\n");

        for (Path path : disable) {
            bat.append("move /Y \"").append(quote(path.toString())).append("\" \"")
                .append(quote(path + ".disabled")).append("\" >nul 2>&1\r\n");
        }

        bat.append("move /Y \"").append(quote(download.toString())).append("\" \"")
            .append(quote(target.toString())).append("\" >nul 2>&1\r\n");

        if (launch != null) {
            bat.append("start \"\" ");

            if (launch.raw != null) {
                // The exact command line the launcher used, quotes included; % is the one batch
                // character that would be eaten, so it is escaped.
                bat.append(escapePercent(launch.raw.replace("\r", "").replace("\n", "")));
            } else {
                bat.append('"').append(quote(launch.program)).append('"');
                for (String argument : launch.arguments) {
                    bat.append(" \"").append(quote(argument)).append('"');
                }
            }

            bat.append("\r\n");
        }

        bat.append("del \"%~f0\" >nul 2>&1\r\n");

        return bat.toString();
    }

    private static String shell(Path download, Path target, List<Path> disable, Launch launch, long pid, String workingDir) {
        StringBuilder sh = new StringBuilder();

        sh.append("#!/bin/sh\n");
        sh.append("cd ").append(shellQuote(workingDir)).append(" || exit 1\n");
        sh.append("while kill -0 ").append(pid).append(" 2>/dev/null; do sleep 1; done\n");

        for (Path path : disable) {
            sh.append("mv -f ").append(shellQuote(path.toString())).append(' ')
                .append(shellQuote(path + ".disabled")).append('\n');
        }

        sh.append("mv -f ").append(shellQuote(download.toString())).append(' ')
            .append(shellQuote(target.toString())).append('\n');

        if (launch != null) {
            sh.append("nohup ").append(shellQuote(launch.program));
            for (String argument : launch.arguments) {
                sh.append(' ').append(shellQuote(argument));
            }
            sh.append(" >/dev/null 2>&1 &\n");
        }

        sh.append("rm -f \"$0\"\n");

        return sh.toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Batch has no escape for a quote inside a quoted string; a quote cannot occur in these paths. */
    private static String quote(String text) {
        return text.replace("\"", "");
    }

    private static String escapePercent(String text) {
        return text.replace("%", "%%");
    }

    private static String shellQuote(String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }
}
