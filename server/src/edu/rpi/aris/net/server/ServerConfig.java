package edu.rpi.aris.net.server;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public class ServerConfig {

    private static final String STORAGE_CONFIG = "storage-dir";
    private static final String LOG_CONFIG = "log-dir";
    private static final String CONFIG_DIR = "config-dir";
    private static final String CA_CONFIG = "ca";
    private static final String KEY_CONFIG = "key";
    private static final String DOMAIN_KEY = "domain";
    private static final String DATABASE_NAME_CONFIG = "db-name";
    private static final String DATABASE_USER_CONFIG = "db-user";
    private static final String DATABASE_PASS_CONFIG = "db-pass";
    private static final String DATABASE_HOST_CONFIG = "db-host";
    private static final String DATABASE_PORT_CONFIG = "db-port";

    private static ServerConfig instance;
    private static Logger logger = LogManager.getLogger(ServerConfig.class);
    private File configFile = new File(System.getProperty("user.home"), "aris.cfg");
    private File storageDir, logDir, caFile, keyFile;
    private String dbHost, dbName, dbUser, dbPass, domain;
    private int dbPort;
    private HashMap<String, String> configOptions = new HashMap<>();

    private ServerConfig() throws IOException {
        if (SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC)
            configFile = new File("/etc/aris.cfg");
        load();
    }

    public static ServerConfig getInstance() throws IOException {
        if (instance == null)
            instance = new ServerConfig();
        return instance;
    }

    private void readFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("#"))
                    line = line.substring(0, line.indexOf('#'));
                line = line.trim();
                if (line.length() <= 0)
                    continue;
                if (!line.contains(" ")) {
                    logger.fatal("Invalid line in config file: " + line);
                    System.exit(1);
                }
                String key = line.substring(0, line.indexOf(' '));
                String value = line.substring(line.indexOf(' ') + 1);
                configOptions.put(key, value);
            }
        }
    }

    private void load() throws IOException {
        configOptions.clear();
        if (!configFile.exists()) {
            logger.fatal("Server configuration file does not exist: " + configFile.getCanonicalPath());
            System.exit(1);
        }
        readFile(configFile);
        String confDirStr;
        if ((confDirStr = configOptions.remove(CONFIG_DIR)) != null) {
            File confDir = new File(confDirStr);
            if (!confDir.exists())
                throw new FileNotFoundException("Specified configuration directory \"" + confDir.getCanonicalPath() + "\" does not exist");
            if (!confDir.isDirectory())
                throw new IOException("Specified configuration directory \"" + confDir.getCanonicalPath() + "\" is not a directory");
            File[] confFiles = confDir.listFiles();
            if (confFiles != null) {
                Arrays.sort(confFiles, (o1, o2) -> {
                    Comparator<String> comp = Comparator.reverseOrder();
                    return comp.compare(o1.getName(), o2.getName());
                });
                for (File file : confFiles)
                    readFile(file);
            }
        }
        // required configs
        dbPass = getConfigOption(DATABASE_PASS_CONFIG, null, false);
        storageDir = new File(getConfigOption(STORAGE_CONFIG, null, false));
        logDir = new File(getConfigOption(LOG_CONFIG, null, false));
        updateLogger();
        // optional configs
        String caStr = getConfigOption(CA_CONFIG, null, true);
        if (caStr != null)
            caFile = new File(caStr);
        String keyStr = getConfigOption(KEY_CONFIG, null, true);
        if (keyStr != null)
            keyFile = new File(keyStr);
        domain = getConfigOption(DOMAIN_KEY, null, true);
        dbName = getConfigOption(DATABASE_NAME_CONFIG, "aris", true);
        dbUser = getConfigOption(DATABASE_USER_CONFIG, "aris", true);
        dbHost = getConfigOption(DATABASE_HOST_CONFIG, "localhost", true);
        String portStr = getConfigOption(DATABASE_PORT_CONFIG, "5432", true);
        try {
            dbPort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            logger.fatal("Invalid server port: " + portStr);
            System.exit(1);
        }
        if (dbPort <= 0 || dbPort > 65535) {
            logger.fatal("Invalid server port: " + portStr);
            System.exit(1);
        }
        if (configOptions.size() > 0)
            logger.error("Unknown configuration options: " + StringUtils.join(configOptions.keySet(), ", "));
    }

    private void updateLogger() throws IOException {
        String logPath = logDir.getCanonicalPath();
        logPath += logPath.endsWith(File.separator) ? "" : File.separator;
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        ConsoleAppender consoleAppender = config.getAppender("console");
        PatternLayout consolePattern = (PatternLayout) consoleAppender.getLayout();
        TimeBasedTriggeringPolicy triggeringPolicy = TimeBasedTriggeringPolicy.newBuilder().withInterval(1).withModulate(true).build();
        PatternLayout patternLayout = PatternLayout.newBuilder().withPattern(consolePattern.getConversionPattern()).build();
        RollingFileAppender rollingFileAppender = RollingFileAppender.newBuilder()
                .withName("fileLogger")
                .withFileName(logPath + "aris.log")
                .withFilePattern(logPath + "aris-%d{yyyy-MM-dd}.log.gz")
                .withPolicy(triggeringPolicy)
                .withLayout(patternLayout)
                .setConfiguration(config)
                .build();
        rollingFileAppender.start();
        config.addAppender(rollingFileAppender);
        LoggerConfig rootLogger = config.getRootLogger();
        rootLogger.addAppender(config.getAppender("fileLogger"), null, null);
        context.updateLoggers();
    }

    private String getConfigOption(String key, String defaultValue, boolean optional) throws IOException {
        String option = configOptions.remove(key);
        if (option == null && defaultValue == null && !optional) {
            logger.fatal("Configuration (" + configFile.getCanonicalPath() + ") missing option: " + key);
            System.exit(1);
        }
        return option == null ? defaultValue : option;
    }

    public File getStorageDir() {
        return storageDir;
    }

    public File getBaseLogfile() {
        return logDir;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbHost() {
        return dbHost;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPass() {
        return dbPass;
    }

    public int getDbPort() {
        return dbPort;
    }

    public File getCaFile() {
        return caFile;
    }

    public void setCaFile(File caFile) {
        this.caFile = caFile;
    }

    public File getKeyFile() {
        return keyFile;
    }

    public void setKeyFile(File keyFile) {
        this.keyFile = keyFile;
    }

    public String getDomain() {
        return domain;
    }
}
