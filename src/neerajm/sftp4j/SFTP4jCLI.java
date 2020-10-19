package neerajm.sftp4j;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import neerajm.cli.CLI;
import neerajm.configprovider.ConfigProvider;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

public class SFTP4jCLI extends CLI implements Closeable{
    private String user, host, password, commandFile;
    private boolean verbose = false, savePassword = false, ignoreDefaultUser = false, disableInputs = false;
    private SFTP4j mSftp4j;
    private Logger logger;
    private ConfigProvider configProvider = new ConfigProvider();

    @Override
    public boolean opts(String arg, ArrayList<String> value) {
        try{
            switch (arg){
                case "u":
                case "user":
                    user = value.get(0);
                    break;
                case "p":
                case "password":
                    password=value.get(0);
                    break;
                case "h":
                case "host":
                    host=value.get(0);
                    break;
                case "f":
                case "file":
                    commandFile = value.get(0);
                    break;
                case "v":
                case "verbose":
                    verbose = true;
                    break;
                case "s":
                case "save":
                    savePassword = true;
                    break;
                case "i":
                case "ignore":
                    ignoreDefaultUser = true;
                    break;
                case "q":
                case "quiet":
                    disableInputs=true;
                    break;
                default:
                    return false;
            }
        }catch (Exception e){
            logger.error("Invalid arguements, please look help");
            logger.debug(e.getMessage(),e);
            return false;
        }
        return true;
    }

    public SFTP4jCLI(String[] args) throws IOException {
        logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.ERROR);
        parseArgs(args);

        if (verbose) logger.setLevel(Level.INFO);
        else logger.setLevel(Level.ERROR);

        try {
            configProvider.load(SFTP4j.CONFIG_FILE_SFTP);
        } catch (IOException e) {
            logger.error("SFTP4j : config files doesn't exists.");
        }
        connect();
    }

    private void connect() {
        if (user == null)
            user = getMissingParams("Please enter sftp username.", false, SFTP4j.CONFIG_KEY_USER);
        if (host == null)
            host = getMissingParams("Please enter sftp hostname for user - " + user, false, SFTP4j.getConfigKeyHost(user));
        if (password == null)
            password = getMissingParams("Please enter sftp password for user - " + user, true, SFTP4j.getConfigKeyPassword(user));
        try {
            mSftp4j = new SFTP4j(user, host, password, logger);
        } catch (IOException e) {
            logger.error("SFTP4j : Failed to connect.", e);
            System.exit(1);
        }
    }

    private String getMissingParams(String msg, boolean hidden, String key) {
        String value;
        try {
            if (ignoreDefaultUser) throw new IOException();
            value = configProvider.getConfig(key);
        } catch (Exception e) {
            value = readInput(msg, hidden);
            try {
                if (savePassword) {
                    configProvider.setConfig(SFTP4j.CONFIG_FILE_SFTP, key, value, hidden);
                }
            } catch (IOException e1) {
                logger.error("SFTP4j : unable to save sftp configs.", e);
            }
        }
        return value;
    }

    public String readInput(String msg, boolean hidden) {
        if (disableInputs) {
            logger.error(msg + " ###Input is disabled.###");
            return "";
        }
        Console console = System.console();
        System.out.println(msg);
        if (console == null) {
            return new Scanner(System.in).nextLine();
        }
        if (!hidden) return console.readLine();
        return new String(console.readPassword());
    }

    public void readAll() throws IOException {
        if (commandFile != null) {
            File file = new File(commandFile);
            if (!file.exists())
                logger.error(commandFile + " not found can't proceed", new IOException());
            readAll(new FileInputStream(commandFile));
        }
        else readAll(System.in);
    }

    public void readAll(InputStream inputStream) {
        Scanner sc = new Scanner(inputStream);
        System.out.print("SFTP4j:" + mSftp4j.getCd() + ">");
        while (sc.hasNext()) {
            String line = sc.nextLine();
            if (line.equals("bye")) break;
            try {
                line = line.replaceAll("[\\s]+"," ").trim();
                if(!line.isEmpty())
                    mSftp4j.exec(line.split(" "));
            } catch (Exception e) {
                logger.error("SFTP4j : Can't execute that command.", e);
            }
            System.out.print("SFTP4j:" + mSftp4j.getCd() + ">");
        }
    }

    @Override
    public void close() {
        if(mSftp4j!=null) mSftp4j.close();
    }

    public static void main(String[] args) throws IOException {
        SFTP4jCLI cli = new SFTP4jCLI(args);
        cli.readAll();
        System.exit(0);
    }
}
