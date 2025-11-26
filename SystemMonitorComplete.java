import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SystemMonitorComplete {
    // Configurări generale
    private static final String RESOURCES_LOG = "resurse_sistem.log";
    private static final String PROCESSES_LOG = "procese_gestionate.log";
    private static final int RESOURCE_CHECK_SECONDS = 5;
    private static final int PROCESS_CHECK_SECONDS = 60;
    private static final int TIME_LIMIT_MINUTES = 30;

    // Cuvinte cheie pentru aplicații de monitorizat
    private static final String[] KEYWORDS = {
            "game", "joc", "steam", "epic", "minecraft", "fortnite", "league",
            "chrome", "firefox", "edge", "safari", "brave", "opera",
            "browser", "navigator"
    };

    // Map pentru a urmări când a pornit fiecare proces
    private static ConcurrentHashMap<String, Long> processStartTimes = new ConcurrentHashMap<>();
    private static OperatingSystemMXBean osBean;

    public static void main(String[] args) {
        // Adaugă în startup la prima rulare
        if (args.length == 0 || !args[0].equals("--no-startup")) {
            addToStartup();
        }

        System.out.println("------MONITOR SISTEM COMPLET------");
        System.out.println();
        System.out.println("Monitorizare resurse: la fiecare " + RESOURCE_CHECK_SECONDS + " secunde");
        System.out.println("Gestionare procese: la fiecare " + PROCESS_CHECK_SECONDS + " secunde");
        System.out.println("Limită timp procese: " + TIME_LIMIT_MINUTES + " minute");
        System.out.println("Cuvinte cheie: " + Arrays.toString(KEYWORDS));
        System.out.println();
        System.out.println("Apăsați Ctrl+C pentru a opri programul");
        System.out.println();

        osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        logToProcessFile("Program pornit - Monitorizare activă");

        // Pornește thread-ul pentru monitorizare resurse
        Thread resourceMonitor = new Thread(() -> monitorResources());
        resourceMonitor.setDaemon(true);
        resourceMonitor.start();

        // Pornește thread-ul pentru gestionare procese
        Thread processManager = new Thread(() -> manageProcesses());
        processManager.setDaemon(true);
        processManager.start();

        // Menține programul în rulare
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("\nProgram oprit.");
            logToProcessFile("Program oprit");
        }
    }

    private static void monitorResources() {
        while (true) {
            try {
                // Obține informații despre CPU
                double cpuLoad = osBean.getCpuLoad() * 100;

                // Obține informații despre RAM
                long totalMemory = osBean.getTotalMemorySize();
                long freeMemory = osBean.getFreeMemorySize();
                long usedMemory = totalMemory - freeMemory;

                double freeMemoryGB = freeMemory / (1024.0 * 1024.0 * 1024.0);
                double usedMemoryGB = usedMemory / (1024.0 * 1024.0 * 1024.0);
                double totalMemoryGB = totalMemory / (1024.0 * 1024.0 * 1024.0);
                double memoryUsagePercent = (usedMemory * 100.0) / totalMemory;

                // Afișează în consolă
                String timestamp = getCurrentTime();
                System.out.println("RESURSE SISTEM");
                System.out.println("- Timp: " + timestamp);
                System.out.printf("- CPU: %.2f%%\n", cpuLoad);
                System.out.printf("- RAM Liberă: %.2f GB / %.2f GB\n", freeMemoryGB, totalMemoryGB);
                System.out.printf("- RAM Utilizată: %.2f GB (%.2f%%)\n", usedMemoryGB, memoryUsagePercent);
                System.out.println();

                // Salvează în jurnal
                logToResourceFile(timestamp, cpuLoad, freeMemoryGB, usedMemoryGB,
                        totalMemoryGB, memoryUsagePercent);

                Thread.sleep(RESOURCE_CHECK_SECONDS * 1000);

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("Eroare la monitorizarea resurselor: " + e.getMessage());
            }
        }
    }

    private static void logToResourceFile(String timestamp, double cpuLoad,
                                          double freeMemory, double usedMemory,
                                          double totalMemory, double memoryPercent) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(RESOURCES_LOG, true))) {
            writer.printf("%s | CPU: %.2f%% | RAM Liberă: %.2f GB | RAM Utilizată: %.2f GB | Total: %.2f GB | Utilizare: %.2f%%\n",
                    timestamp, cpuLoad, freeMemory, usedMemory, totalMemory, memoryPercent);
        } catch (IOException e) {
            System.err.println("Eroare la scrierea în jurnal resurse: " + e.getMessage());
        }
    }

    //GESTIONARE PROCESE

    private static void manageProcesses() {
        // Așteaptă puțin la început pentru a nu supraîncărca output-ul
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            return;
        }

        while (true) {
            try {
                checkAndManageProcesses();
                Thread.sleep(PROCESS_CHECK_SECONDS * 1000);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("Eroare la gestionarea proceselor: " + e.getMessage());
            }
        }
    }

    private static void checkAndManageProcesses() {
        String os = System.getProperty("os.name").toLowerCase();
        List<ProcessInfo> processes = getRunningProcesses(os);

        long currentTime = System.currentTimeMillis();
        Set<String> currentProcesses = new HashSet<>();

        boolean foundProcesses = false;

        for (ProcessInfo proc : processes) {
            if (matchesKeyword(proc.name)) {
                foundProcesses = true;
                currentProcesses.add(proc.identifier);

                // Dacă procesul nu e în map, adaugă-l
                if (!processStartTimes.containsKey(proc.identifier)) {
                    processStartTimes.put(proc.identifier, currentTime);
                    String msg = "Proces detectat: " + proc.name + " (PID: " + proc.pid + ")";
                    System.out.println("- " + msg);
                    logToProcessFile(msg);
                } else {
                    // Verifică dacă a depășit limita de timp
                    long startTime = processStartTimes.get(proc.identifier);
                    long runningMinutes = (currentTime - startTime) / (1000 * 60);

                    if (runningMinutes >= TIME_LIMIT_MINUTES) {
                        String msg = String.format("Proces %s (PID: %s) rulează de %d minute. Se închide...",
                                proc.name, proc.pid, runningMinutes);
                        System.out.println("!" + msg);
                        logToProcessFile(msg);

                        if (killProcess(proc.pid, os)) {
                            String successMsg = "Proces " + proc.name + " închis cu succes";
                            System.out.println("+" + successMsg);
                            logToProcessFile(successMsg);
                            processStartTimes.remove(proc.identifier);
                        } else {
                            String errorMsg = "EROARE: Nu s-a putut închide procesul " + proc.name;
                            System.out.println("✗ " + errorMsg);
                            logToProcessFile(errorMsg);
                        }
                    } else {
                        System.out.printf("[%s] %s rulează de %d/%d minute\n",
                                getCurrentTime(), proc.name, runningMinutes, TIME_LIMIT_MINUTES);
                    }
                }
            }
        }

        if (foundProcesses) {
            System.out.println();
        }

        // Curăță procesele care nu mai rulează
        processStartTimes.keySet().removeIf(key -> !currentProcesses.contains(key));
    }

    private static List<ProcessInfo> getRunningProcesses(String os) {
        List<ProcessInfo> processes = new ArrayList<>();

        try {
            Process process;
            if (os.contains("win")) {
                process = Runtime.getRuntime().exec("tasklist /FO CSV /NH");
            } else {
                process = Runtime.getRuntime().exec("ps -eo pid,comm");
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                ProcessInfo info = parseProcessLine(line, os);
                if (info != null) {
                    processes.add(info);
                }
            }

            reader.close();
            process.waitFor();

        } catch (Exception e) {
            logToProcessFile("EROARE la citirea proceselor: " + e.getMessage());
        }

        return processes;
    }

    private static ProcessInfo parseProcessLine(String line, String os) {
        try {
            if (os.contains("win")) {
                String[] parts = line.split("\",\"");
                if (parts.length >= 2) {
                    String name = parts[0].replace("\"", "").toLowerCase();
                    String pid = parts[1].replace("\"", "");
                    return new ProcessInfo(pid, name);
                }
            } else {
                line = line.trim();
                String[] parts = line.split("\\s+", 2);
                if (parts.length >= 2) {
                    String pid = parts[0];
                    String name = parts[1].toLowerCase();
                    return new ProcessInfo(pid, name);
                }
            }
        } catch (Exception e) {
            // Ignoră liniile invalide
        }
        return null;
    }

    private static boolean matchesKeyword(String processName) {
        String lower = processName.toLowerCase();
        for (String keyword : KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean killProcess(String pid, String os) {
        try {
            Process process;
            if (os.contains("win")) {
                process = Runtime.getRuntime().exec("taskkill /F /PID " + pid);
            } else {
                process = Runtime.getRuntime().exec("kill -9 " + pid);
            }

            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            logToProcessFile("EROARE la închiderea procesului " + pid + ": " + e.getMessage());
            return false;
        }
    }

    private static void logToProcessFile(String message) {
        String timestamp = getCurrentTime();
        String logEntry = timestamp + " | " + message;

        try (PrintWriter writer = new PrintWriter(new FileWriter(PROCESSES_LOG, true))) {
            writer.println(logEntry);
        } catch (IOException e) {
            System.err.println("Eroare la scrierea în jurnal procese: " + e.getMessage());
        }
    }

    // ============= UTILITARE =============

    private static String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static void addToStartup() {
        String os = System.getProperty("os.name").toLowerCase();
        String jarPath = getJarPath();

        try {
            if (os.contains("win")) {
                String regKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
                String appName = "SystemMonitorComplete";
                String command = String.format("reg add \"%s\" /v %s /t REG_SZ /d \"%s\" /f",
                        regKey, appName, "javaw -jar \"" + jarPath + "\" --no-startup");

                Process process = Runtime.getRuntime().exec(command);
                process.waitFor();
                System.out.println("✓ Adăugat în startup (Windows)");

            } else if (os.contains("mac")) {
                String homeDir = System.getProperty("user.home");
                String plistPath = homeDir + "/Library/LaunchAgents/com.systemmonitorcomplete.plist";

                String plistContent = String.format(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                                "<plist version=\"1.0\">\n" +
                                "<dict>\n" +
                                "    <key>Label</key>\n" +
                                "    <string>com.systemmonitorcomplete</string>\n" +
                                "    <key>ProgramArguments</key>\n" +
                                "    <array>\n" +
                                "        <string>java</string>\n" +
                                "        <string>-jar</string>\n" +
                                "        <string>%s</string>\n" +
                                "        <string>--no-startup</string>\n" +
                                "    </array>\n" +
                                "    <key>RunAtLoad</key>\n" +
                                "    <true/>\n" +
                                "</dict>\n" +
                                "</plist>", jarPath);

                try (PrintWriter writer = new PrintWriter(plistPath)) {
                    writer.print(plistContent);
                }
                System.out.println("✓ Adăugat în startup (macOS)");

            } else if (os.contains("nux")) {
                String homeDir = System.getProperty("user.home");
                String autostartDir = homeDir + "/.config/autostart";
                new File(autostartDir).mkdirs();

                String desktopPath = autostartDir + "/systemmonitorcomplete.desktop";
                String desktopContent = String.format(
                        "[Desktop Entry]\n" +
                                "Type=Application\n" +
                                "Name=System Monitor Complete\n" +
                                "Exec=java -jar \"%s\" --no-startup\n" +
                                "Hidden=false\n" +
                                "NoDisplay=false\n" +
                                "X-GNOME-Autostart-enabled=true\n", jarPath);

                try (PrintWriter writer = new PrintWriter(desktopPath)) {
                    writer.print(desktopContent);
                }

                // Setează permisiuni de execuție
                new File(desktopPath).setExecutable(true);

                System.out.println("✓ Adăugat în startup (Linux)");
                System.out.println("  Locație: " + desktopPath);
            }
        } catch (Exception e) {
            System.err.println("✗ Eroare la adăugarea în startup: " + e.getMessage());
        }
    }

    private static String getJarPath() {
        try {
            String path = SystemMonitorComplete.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            return new File(path).getAbsolutePath();
        } catch (Exception e) {
            return System.getProperty("user.dir") + "/SystemMonitorComplete.jar";
        }
    }

    // Clasă helper pentru informații despre proces
    static class ProcessInfo {
        String pid;
        String name;
        String identifier;

        ProcessInfo(String pid, String name) {
            this.pid = pid;
            this.name = name;
            this.identifier = pid + "_" + name;
        }
    }
}