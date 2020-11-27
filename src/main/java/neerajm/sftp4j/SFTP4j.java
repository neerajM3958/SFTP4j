package neerajm.sftp4j;

import ch.qos.logback.classic.Logger;
import neerajm.configprovider.ConfigProvider;
import net.schmizz.keepalive.KeepAliveProvider;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.*;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;

public class SFTP4j implements Closeable{
    public static final String CONFIG_FILE_SFTP = "sftp4j.conf", CONFIG_KEY_USER = "sftp_user";
    public static final String LOGGER_NAME="SFTP4j";
    private SSHClient mClient;
    private SFTPClient mSftpClient;
    public static final String COMMAND_GET = "get",
            COMMAND_PUT = "put",
            COMMAND_RM = "rm",
            COMMAND_MKDIRS = "mkdirs",
            COMMAND_READ = "read",
            COMMAND_LS = "ls",
            COMMAND_CD = "cd",
            COMMAND_PWD = "pwd",
            COMMAND_HELP = "help";
    private final String pathSeprator = File.separator;
    private String cd = pathSeprator;
    public static final String TYPE_DIRECTORY = "DIRECTORY";
    private Logger mLogger;
    private String mUser, mPassword, mHost;

    public SFTP4j() throws IOException {
        ConfigProvider configProvider = new ConfigProvider().load(CONFIG_FILE_SFTP);
        String user = configProvider.getConfig(CONFIG_KEY_USER);
        String host = configProvider.getConfig(getConfigKeyHost(user));
        String password = configProvider.getConfig(getConfigKeyPassword(user));
        init(user, host, password);
    }

    public SFTP4j(String user, String host, String password) throws IOException {
        init(user, host, password);
    }

    public static String getConfigKeyHost(String user) {
        return String.format("%s_host", user);
    }

    public static String getConfigKeyPassword(String user) {
        return String.format("%s_pwd", user);
    }

    private void init(String user, String host, String password) throws IOException {
        mLogger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        mUser = user;
        mHost = host;
        mPassword = password;
        DefaultConfig defaultConfig = new DefaultConfig();
        defaultConfig.setKeepAliveProvider(KeepAliveProvider.KEEP_ALIVE);
        reconnect();
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    writef("/.sftp4j_dummy_file","",true);

                } catch (IOException e) {
                    this.cancel();
                }
            }
        }, 0, 5000);

    }

    public SFTP4j reconnect() throws IOException {
        if(mClient==null || !mClient.isConnected()){
            mClient = new SSHClient();
            mClient.addHostKeyVerifier(new PromiscuousVerifier());
            mClient.setConnectTimeout(5000);
            mClient.connect(mHost);
            mClient.getConnection().getKeepAlive().setKeepAliveInterval(5); //every 60sec
            mClient.authPassword(mUser, mPassword);
            mSftpClient = mClient.newSFTPClient();
            mLogger.info("connection established.");
        }
        return this;
    }

    public SFTPClient getSftpClient() {
        return mSftpClient;
    }

    public void close() {
        try {
            mSftpClient.close();
            mClient.close();
            mClient.disconnect();
            mLogger.info("connection closed.");
        } catch (IOException e) {
            mLogger.error("Failed to close sftp connection.", e);
        }

    }

    private String trimPath(String... paths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            String p = paths[i];
            if (p.isEmpty()) continue;
            sb.append(p);
            if (sb.length() > 0 && i < paths.length - 1) sb.append(pathSeprator);
        }
        return sb.toString().replaceAll("[\\\\/]+", Matcher.quoteReplacement(pathSeprator));
    }

    public boolean isDir(String path) throws IOException {
        return mSftpClient.type(path).name().equals(TYPE_DIRECTORY);
    }

    public void mkdirs(String des) throws IOException {
        des = trimPath(des);
        if(mSftpClient.statExistence(des)!=null)return;
        if (des.equals(pathSeprator)) return;
        Path path = Paths.get(des);
        StringBuilder l = new StringBuilder();
        boolean flag = true;
        for(Path x:path) {
            if(flag){
                flag=false;
                l = new StringBuilder(x.toString());
            }else l.append("/").append(x);
            //System.out.println("----" + l);
            mSftpClient.mkdirs(l.toString());
        }
    }

    public void getf(String src, String des) throws IOException {
        src = trimPath(src);
        des = trimPath(des);
        mLogger.info(String.format("getf %s %s\n", src, des));
        File file = new File(des);
        File pfile = file.getParentFile();
        if (pfile == null || pfile.mkdirs() || pfile.exists()) {
            mSftpClient.get(src, des);
        } else mLogger.error("Can't able to create base path of file - " + des, new IOException());
    }

    public void get(String src, String re, String des) throws IOException {
        src = trimPath(src);
        des = trimPath(des);
        if (des.isEmpty()) des = "." + pathSeprator;
        final String regex = re.isEmpty() ? ".*" : re;
        List<RemoteResourceInfo> files = ls(src, regex);
        if(files.size()==0){
            mLogger.error(String.format("file doesn't exists :  %s,%s", src, regex));
        }
        else{
            for (RemoteResourceInfo rri : files) {
                String ts = trimPath(rri.getPath()), td;
                if (rri.isDirectory()) {
                    td = trimPath(des, rri.getPath().substring(src.length()));
                    get(ts, "", td);
                } else {
                    td = trimPath(des, rri.getName());
                    getf(ts, td);
                }
            }
        }
    }

    public void putf(String src, String des) throws IOException {
        src = trimPath(src);
        des = trimPath(des);
        mLogger.info(String.format("putf %s %s\n", src, des));
        if (mSftpClient.statExistence(des) != null)
            mSftpClient.rm(des);
        mSftpClient.put(src, des);
    }

    public void put(String src, String re, String des) throws IOException {
        src = src.trim();
        des = des.trim();
        if (src.isEmpty()) src = "." + pathSeprator;
        File src_file = new File(src);
        final String regex = re.isEmpty() ? ".*" : re;
        File[] files = src_file.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches(regex);
            }
        });
        if (files == null || files.length==0) {
            mLogger.error(String.format("file doesn't exists :  %s,%s", src, regex));
        }
        else {
            String ts,td;
            for (File file : files) {
                ts = file.getPath();
                td = trimPath(des, file.getPath().substring(src.length()));
                if (!file.isDirectory()) {
                    mkdirs(Paths.get(td).getParent().toString());
                    putf(ts,td);
                } else {
                    put(ts, "", td);
                }
            }
        }
    }

    public boolean rm(String path, String re) throws IOException {
        if (mSftpClient.statExistence(path) != null) {
            final String regex = re.isEmpty() ? ".*" : re;
            List<RemoteResourceInfo> list = ls(path, regex);
            if(list!=null && list.size()>0){
                for (RemoteResourceInfo rri : list) {
                    if (!rri.isDirectory()) {
                        mSftpClient.rm(rri.getPath());
                    } else {
                        rm(rri.getPath(), "");
                        if(ls(rri.getPath()).size()==0)
                            mSftpClient.rmdir(rri.getPath());
                    }
                }
                mLogger.info( String.format("files removed at path [%s, %s]", path, regex));
                return true;
            }
        } else {
            mLogger.error("SFTP4j : rm - " + path + " doesn't exist.", new IOException());
        }
        return false;
    }

    public String readf(String path) throws IOException {
        if (mSftpClient.statExistence(path) == null)
            throw new FileNotFoundException(String.format("SFTP4j : readf - path \"%s\" not exists", path));
        RemoteFile rfile = mSftpClient.open(path, EnumSet.of(OpenMode.READ));
        RemoteFile.RemoteFileInputStream rfis = rfile.new RemoteFileInputStream();
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[8192];
        while (rfis.read(buffer, 0, buffer.length) != -1) {
            sb.append(new String(buffer));
        }
        rfis.close();
        rfile.close();
        return sb.toString();
    }

    public void writef(String path, String data, boolean append) throws IOException {
        try {
            path = path.trim();
            File file = new File(path);
            mkdirs(file.getParentFile().getPath());
            if (mSftpClient.statExistence(file.getParent()) == null)
                throw new IOException(String.format("SFTP4j : writef - file not found - \"%s\"", file.getParent()));
            Set<OpenMode> openmode = EnumSet.of(OpenMode.CREAT, OpenMode.WRITE);
            if (!append)
                openmode.add(OpenMode.TRUNC);
            RemoteFile rfile = mSftpClient.open(path, openmode);
            RemoteFile.RemoteFileOutputStream rsos = rfile.new RemoteFileOutputStream();
            byte[] buff = data.getBytes();
            rfile.write(rfile.length(), buff, 0, buff.length);
            rsos.close();
            rfile.close();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public void rename(String oldPath, String newPath) throws IOException {
        mSftpClient.rename(oldPath, newPath);
    }

    public List<RemoteResourceInfo> ls(String path) throws IOException {
        return ls(path, "");
    }

    public List<RemoteResourceInfo> ls(String path, String re) throws IOException {
        FileAttributes fa = mSftpClient.statExistence(path);
        if (fa == null)
            throw new FileNotFoundException(String.format("SFTP4j : ls - path \"%s\" not exists", path));
        if(!fa.getType().name().equals(TYPE_DIRECTORY)){
            Path pathObj = Paths.get(path);
            path = pathObj.getParent().toString();
            re = pathObj.getFileName().toString();
        }
        final String regex = re.isEmpty() ? ".*" : re;
        return mSftpClient.ls(path, new RemoteResourceFilter() {
            @Override
            public boolean accept(RemoteResourceInfo resource) {
                return resource.getName().matches(regex);
            }
        });
    }

    private String lsString(String path, String regex) {
        path = path.isEmpty() ? "/" : path;
        StringBuilder sb = new StringBuilder();
        try{
            List<RemoteResourceInfo> list = ls(path, regex);
            Collections.sort(list, new Comparator<RemoteResourceInfo>() {
                @Override
                public int compare(RemoteResourceInfo o1, RemoteResourceInfo o2) {
                    long diff = o1.getAttributes().getMtime() - o2.getAttributes().getMtime();
                    if (diff < 0) return -1;
                    else if (diff > 0) return 1;
                    return 0;
                }
            });
            sb.append(String.format("path : %s\n", path));
            sb.append(String.format("total : %d\n", list.size()));
            sb.append(String.format("%-3s %-16s %s\n", "Dir", "Date", "Name"));
            for (RemoteResourceInfo i : list) {
                String date = new SimpleDateFormat("MM/dd/yyyy HH:mm").format(new Date(i.getAttributes().getMtime() * 1000));
                sb.append(String.format("%-3s %-16s %s\n", i.isDirectory() ? 'd' : '-', date, i.getName()));
            }
        }catch (Exception e){
            mLogger.error(e.getMessage());
        }
        return sb.toString();
    }


    public void exec(String[] args) throws IOException {
        String out;
        for(int i=0;i<args.length;i++) args[i] = args[i].replaceAll("[\"']","");
        String src = args.length >= 2 ? trimPath(args[1]) : "";
        String des = args.length >= 3 ? trimPath(args[2]) : "";
        String path = src, regex = "";
        if (!src.isEmpty()) {
            int in = src.lastIndexOf(pathSeprator) + 1;
            path = src.substring(0, in);
            regex = src.substring(in).replaceAll(";", Matcher.quoteReplacement("\\"));
        }
        switch (args[0]) {
            case COMMAND_GET: // src des
                if(regex.length()==0){
                   mLogger.error("get command need argument : [src path/name/regex], [des path]");
                }
                else get(appendPath(path), regex, des);
                break;
            case COMMAND_PUT: // src des
                if(regex.length()==0){
                    mLogger.error("put command need argument : [src path/name/regex], [des path]");
                }
                else put(path, regex, appendPath(des));
                break;
            case COMMAND_MKDIRS: // src
                mkdirs(appendPath(src));
                break;
            case COMMAND_RM: // src
                if(regex.length()==0){
                mLogger.error("rm command need argument : [src path/name/regex]");
                }else if(!rm(appendPath(path), regex)){
                    mLogger.error(String.format("SFTP4j : rm - [%s, %s] doesn't exist.",path, regex));
                }
                break;
            case COMMAND_READ: // src
                out = readf(appendPath(src));
                System.out.println(out);
                break;
            case COMMAND_LS: //src
                out = lsString(appendPath(path),regex);
                System.out.println(out);
                break;
            case COMMAND_CD: //src
                des = args[1];
                String tcd = appendPath(des);
                FileAttributes stat = mSftpClient.statExistence(tcd);
                if (stat == null) {
                    mLogger.error("Path doesn't exists - " + tcd);
                } else if (!stat.getType().name().equals("DIRECTORY")) {
                    mLogger.error(tcd + " is not a directory.");
                } else {
                    cd = tcd;
                }
                break;
            case COMMAND_PWD:
                System.out.println(cd);
                break;
            case COMMAND_HELP:
                SFTP4jCLI.help();
                break;
            default:
                StringBuilder sb = new StringBuilder();
                for (String s : args) {
                    sb.append(s).append(" ");
                }
                mLogger.error("Invalid input to exec - " + sb.toString());
                mLogger.error("type help for usage information.");
        }
    }

    private String appendPath(String des) {
        String tcd;
        if (!des.startsWith(pathSeprator))
            if( cd.equals(pathSeprator) )
                tcd = cd + des;
            else tcd = cd + pathSeprator + des;
        else tcd = des;
        return Paths.get(tcd).normalize().toString();
    }

    public String getCd() {
        return cd;
    }
}
