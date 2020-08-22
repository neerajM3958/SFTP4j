package neerajm.sftp4j;

import ch.qos.logback.classic.Logger;
import neerajm.configprovider.ConfigProvider;
import net.schmizz.keepalive.KeepAliveProvider;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.*;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;

public class SFTP4j implements Closeable{
    public static final String CONFIG_FILE_SFTP = "sftp4j.conf", CONFIG_KEY_USER = "sftp_user", CONFIG_KEY_HOST = "sftp_host";
    private SSHClient mClient;
    private SFTPClient mSftpClient;
    public static final String COMMAND_GET = "get",
            COMMAND_PUT = "put",
            COMMAND_RM = "rm",
            COMMAND_MKDIRS = "mkdirs",
            COMMAND_READ = "read",
            COMMAND_LS = "ls",
            COMMAND_CD = "cd",
            COMMAND_PWD = "pwd";
    private final String pathSeprator = File.separator;
    private String cd = pathSeprator;
    public static final String TYPE_DIRECTORY = "DIRECTORY";
    private Logger mLogger;

    public SFTP4j(Logger logger) throws IOException {
        ConfigProvider configProvider = new ConfigProvider(CONFIG_FILE_SFTP);
        String user = configProvider.getConfig(CONFIG_KEY_USER);
        String host = configProvider.getConfig(CONFIG_KEY_HOST);
        String password = configProvider.getConfig(user);
        init(user, host, password, logger);
    }

    public SFTP4j(String user, String host, String password, Logger logger) throws IOException {
        init(user, host, password, logger);
    }

    private void init(String user, String host, String password, Logger logger) throws IOException {
        mLogger = logger;
        DefaultConfig defaultConfig = new DefaultConfig();
        defaultConfig.setKeepAliveProvider(KeepAliveProvider.KEEP_ALIVE);
        mClient = new SSHClient();
        mClient.addHostKeyVerifier(new PromiscuousVerifier());
        mClient.setConnectTimeout(4000);
        mClient.connect(host);
        mClient.getConnection().getKeepAlive().setKeepAliveInterval(5); //every 60sec
        mClient.authPassword(user, password);
        mSftpClient = mClient.newSFTPClient();
        mLogger.info("connection established.");
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

    private String trimPath(String path, String... more){
        return Paths.get(path, more).normalize().toString();
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

    public void get(String src, String des) throws IOException {
        src = trimPath(src);
        des = trimPath(des);
        if (!isDir(src)) {
            getf(src, des);
            return;
        }
        List<RemoteResourceInfo> files = ls(src);
        for (RemoteResourceInfo rri : files) {
            if (!rri.isDirectory()) {
                getf(rri.getPath(), des + "/" + rri.getName());
            } else get(rri.getPath(), trimPath(des, rri.getPath().substring(src.length())));
        }
    }

    public void putf(String src, String des) throws IOException {
        src = trimPath(src);
        des = trimPath(des);
        mLogger.info(String.format("putf %s %s\n", src, des));
        if (mSftpClient.statExistence(des) != null)
            mSftpClient.rm(des);
        //else mkdirs(Paths.get(des).getParent().toString());
        mSftpClient.put(src, des);
    }

    public void put(String src, String des) throws IOException {
        src = src.trim();
        des = des.trim();
        File src_file = new File(src);
        if (!src_file.isDirectory()) {
            putf(src, des);
            return;
        }
        //mkdirs(Paths.get(trimPath(des, src_file.getPath().substring(src.length()))).getParent().toString());
        File[] files = src_file.listFiles();
        if (files != null) {
            String ts,td;
            for (File file : files) {
                ts = file.getPath();
                td = trimPath(des, file.getPath().substring(src.length()));
                if (!file.isDirectory()) {
                    mkdirs(Paths.get(td).getParent().toString());
                    putf(ts,td);
                } else {
                    put(ts,td);
                }
            }
        }

    }

    public void rm(String path) throws IOException {
        if (mSftpClient.statExistence(path) != null) {
            if (!mSftpClient.type(path).name().equals(TYPE_DIRECTORY)) {
                mSftpClient.rm(path);
            } else {
                List<RemoteResourceInfo> list = mSftpClient.ls(path);
                for (RemoteResourceInfo rri : list) {
                    rm(rri.getPath());
                }
                mSftpClient.rmdir(path);
                mLogger.info(path + " removed.");
            }
        } else {
            mLogger.error("SFTP4j : rm - " + path + " doesn't exist.", new IOException());
        }
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
            //        rsos.write(data.getBytes());
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

    private String lsString(String path, String regex) throws IOException {
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
        String src = args.length>= 2 ? args[1].replaceAll("[\\\\/]+",Matcher.quoteReplacement(pathSeprator) ) : "";
        String des = args.length>= 3 ? args[2].replaceAll("[\\\\/]+",Matcher.quoteReplacement(pathSeprator) ) : "";
        if (args[0].equals(COMMAND_GET) || args[0].equals(COMMAND_PUT)) {
            if(des.isEmpty())
                des = Paths.get(args[1]).getFileName().toString();
            else if (des.endsWith(pathSeprator) && !Paths.get(src).getFileName().equals(Paths.get(des).getFileName())){
                des = Paths.get(des,Paths.get(src).getFileName().toString()).toString();
            }
        }
        switch (args[0]) {
            case COMMAND_GET: // src des
                get(appendPath(src), des);
                break;
            case COMMAND_PUT: // src des
                put(src, appendPath(des));
                break;
            case COMMAND_MKDIRS: // src
                mkdirs(appendPath(src));
                break;
            case COMMAND_RM: // src
                rm(appendPath(src));
                break;
            case COMMAND_READ: // src
                out = readf(appendPath(src));
                System.out.println(out);
                break;
            case COMMAND_LS: //src
                String path = src , regex = "";
                if(!src.isEmpty()){
                    int in = src.lastIndexOf(pathSeprator) + 1;
                    path = src.substring(0, in);
                    regex = src.substring(in);
                }
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
            default:
                StringBuilder sb = new StringBuilder();
                for (String s : args) {
                    sb.append(s).append(" ");
                }
                mLogger.error("Invalid input to exec - " + sb.toString());
                SFTP4jCLI.help();
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
