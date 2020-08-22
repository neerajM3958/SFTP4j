package neerajm.sftp4j;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Test {
    public static void main(String[] args) {
        Path p = Paths.get("src/asd/").normalize();
        System.out.println(p);
        StringBuilder l = new StringBuilder();
        boolean flag = true;
        for(Path x:p) {
            if(flag){
                flag=false;
                l = new StringBuilder(x.toString());
            }else l.append("/").append(x);
            System.out.println("----" + l);
        }
    }
}
