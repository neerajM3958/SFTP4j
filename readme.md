    SFTP4j PROGRAM
    Description   : Java based sftp client which provide CLI control as well as programmable interface.
    Author        : Neeraj Malhotra
    Date Created  : 15 March, 2020
    Date Modified : 23  Aug, 2020

    --ABOUT--
        SFTP4j is a java based sftp client developed of SSHJ library. It provides easy CLI interface, similar to default sftp client.
        it supports get, put, rm, mkdirs, read, ls, cd, pwd commands.
        All function supports Regular Expressions for filtering files.
        get and put functions can up/dowloading directories.
        rm and mkdirs function work in a recursive manner.
        read function can print content of file to output.
        Save as well as encrypt credentials and also credentials can be passed through arguements.

        ---THIS LIBRARY ALSO PROVIDE PROGRAMABLE INTERFACE---

    --USAGE--
        -u <username> -h <hostname> -p <password> -f <filename> -v
            -u username [optional] if not passed it will ask and store inside ${HOME}.conf_provider/sftp4j.conf
            -h hostname [optional] if not passed it will ask and store inside ${HOME}.conf_provider/sftp4j.conf
            -p password [optional] if not passed it will ask and store inside ${HOME}.conf_provider/sftp4j.conf
            -f filename, it will read sftp commands from file.
            -v verbose mode, it will show all logs.

        <some data stream> | sftp4j -u <username>
        echo "get srcfile.txt desfile.txt" | sftp4j -u sftpUser -h 127.0.0.1

        {
            get /deployment/executor/ res/executor/
            put /deployment/scripts/ res/scripts/
            get /deployment/scripts_other/10.0.62.12.txt res/file.txt
            put /deployment/conf/10.0.62.12/other.txt res/conf/other.txt
        } | sftp4j -u user -h 0.0.0.0 -p password

    file should contain only program specific commands like -

    get    [source sftp  path] [optional destination local path]
    put    [source local path] [optional destination sftp  path]
    rm     [path] - rm is a recursive function which can delete file or directory.
    mkdirs [path] - mkdirs is a recursive function which can create directory in recursive manner.
    read   [path] - print content of file to output.
    ls     [otional path] - ls displays files present on path. It is equivalent of 'ls -lrt' on unix os.
    cd     [path] - cd change directory to given path. Path can be absolute or relative.
    pwd           - pwd prints working directory.

    here are some examples -
    get /deployment/executor/ res/executor/
    get /deployment/scripts_other/10.0.62.12.txt res/file.txt
    get file.txt
    put /deployment/scripts/ res/scripts/
    put /deployment/conf/10.0.62.12/other.txt res/conf/other.txt