package neerajm.sftp4j;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import neerajm.configprovider.ConfigProvider;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Scanner;

public class SFTP4jCLI implements Closeable{
    private String user, host, password, commandFile;
    private boolean verbose = false, savePassword = false, ignoreDefaultUser = false, disableInputs = false;
    private SFTP4j mSftp4j;
    private Logger logger;
    private ConfigProvider configProvider = new ConfigProvider();

    public SFTP4jCLI(String[] args) throws IOException {
        StringBuilder temp = new StringBuilder();
        for (String s : args)
            temp.append(" ").append(s);
        args = temp.toString().split("-");
        for (String p : args) {
            p = p.trim();
            if (p.startsWith("u "))
                user = p.substring(2).trim();
            else if (p.startsWith("p "))
                password = p.substring(2).trim();
            else if (p.startsWith("h "))
                host = p.substring(2).trim();
            else if (p.startsWith("f "))
                commandFile = p.substring(2).trim();
            else if (p.startsWith("v"))
                verbose = true;
            else if (p.startsWith("s"))
                savePassword = true;
            else if (p.startsWith("i"))
                ignoreDefaultUser = true;
            else if (p.startsWith("q"))
                disableInputs = true;
            else if (!p.isEmpty()) {
                help();
            }
        }

        logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
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
        if (user == null) user = getMissingParams("Please enter sftp username.", false, SFTP4j.CONFIG_KEY_USER);
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
            value = configProvider.getConfig(key, hidden);
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
                line = line.replaceAll("[\\s]+"," ");
                mSftp4j.exec(line.split(" "));
            } catch (Exception e) {
                logger.error("SFTP4j : Can't execute that command.", e);
            }
            System.out.print("SFTP4j:" + mSftp4j.getCd() + ">");
        }
    }

    public static void help() {
        try {
            InputStream in = SFTP4j.class.getClassLoader().getResourceAsStream("res/help.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
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
