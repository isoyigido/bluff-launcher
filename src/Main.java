import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main {
    private Main() {
        throw new UnsupportedOperationException("Main class cannot be instantiated.");
    }

    private static final String OWNER = "isoyigido";
    private static final String REPO = "bluff";
    private static final String ASSET_NAME = "bluff.jar";
    private static final String INSTALL_DIR = System.getenv("LOCALAPPDATA") + File.separatorChar + OWNER + File.separatorChar + REPO;

    public static void main(String[] args) {
        try {
            // Ensure folder exists
            File installFolder = new File(INSTALL_DIR);
            if (!installFolder.exists()) {
                installFolder.mkdirs();
                System.out.println("Generated install directory: " + installFolder.getAbsolutePath());
            }

            String latestTag = getLatestReleaseTag();
            File latestLocalJar = getLatestLocalJar(installFolder);

            if (latestTag == null) {
                if (latestLocalJar == null) JOptionPane.showMessageDialog(null, "Cannot connect to the installation server.", "Error", JOptionPane.ERROR_MESSAGE);
                else runJar(latestLocalJar);

                return;
            }

            if ((latestLocalJar != null) && (compareVersions(getVersionFromName(latestLocalJar.getName()), latestTag) >= 0)) {
                runJar(latestLocalJar);

                return;
            }

            File newJar = new File(installFolder, latestTag + ".jar");
            downloadLatestRelease(newJar);
            runJar(newJar);

        } catch (Exception e) {
            // Print stack trace
            e.printStackTrace();

            // Show error message
            JOptionPane.showMessageDialog(null, "An error occurred: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String getLatestReleaseTag() {
        try {
            URL url = new URI("https://api.github.com/repos/" + OWNER + '/' + REPO + "/releases/latest").toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "Java-Updater");
            connection.connect();

            if (connection.getResponseCode() != 200) return null;

            CharSequence jsonText = new String(connection.getInputStream().readAllBytes());

            Matcher matcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonText);

            if (matcher.find()) {
                return matcher.group(1);
            }

            System.out.println("Unable to find tag_name in JSON response.");

            return null;

        } catch (Exception e) {
            // Print stack trace
            e.printStackTrace();

            // Return null
            return null;
        }
    }

    private static void downloadLatestRelease(File target) throws Exception {
        URL url = new URI("https://github.com/" + OWNER + '/' + REPO + "/releases/latest/download/" + ASSET_NAME).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Java-Updater");

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static File getLatestLocalJar(File folder) {
        File[] jars = folder.listFiles((_, name) -> name.matches("v\\d+\\.\\d+\\.\\d+\\.jar"));

        if ((jars == null) || (jars.length == 0)) return null;

        return Arrays.stream(jars).max(Comparator.comparing(f -> getVersionFromName(f.getName()), Main::compareVersions)).orElse(null);
    }

    private static String getVersionFromName(CharSequence name) {
        Matcher m = Pattern.compile("(\\d+\\.\\d+\\.\\d+)").matcher(name);
        return m.find() ? m.group(1) : null;
    }

    private static int compareVersions(String v1, String v2) {
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        int[] a = Arrays.stream(v1.replace("v", "").split("\\.")).mapToInt(Integer::parseInt).toArray();
        int[] b = Arrays.stream(v2.replace("v", "").split("\\.")).mapToInt(Integer::parseInt).toArray();

        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int ai = (i < a.length) ? a[i] : 0;
            int bi = (i < b.length) ? b[i] : 0;
            if (ai != bi) return ai - bi;
        }

        return 0;
    }

    private static void runJar(File jar) throws IOException {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();

        new ProcessBuilder(javaBin, "-jar", jar.getAbsolutePath())
                .inheritIO()
                .start();
    }
}