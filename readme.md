    SFTP4j PROGRAM
    Description   : Java based sftp client which provide CLI control as well as programmable interface.
    Author        : Neeraj Malhotra
    Date Created  : 15 March, 2020
    Date Modified : 23  Aug, 2020

    ---ABOUT---
        SFTP4j is a java based sftp client developed over SSHJ library. It provides easy CLI interface, similar to default sftp client.
        it supports get, put, rm, mkdirs, read, ls, cd, pwd, bye commands.
        All function supports Regular Expressions for filtering files.
        get and put functions can up/download directories.
        rm and mkdirs function work in a recursive manner.
        read function can print content of file to output.
        Save as well as encrypt credentials and also credentials can be passed as arguements for the purpose of Automation.

        ---THIS LIBRARY ALSO PROVIDE PROGRAMABLE INTERFACE---
    ---ABOUT---

    ---USAGE---
        -u <username> -h <hostname> -p <password> -f <filename> -v -s -i -q
            -u username [optional] If not passed it will ask
            -h hostname [optional] If not passed it will ask
            -p password [optional] If not passed it will ask
            -f filename [optional] It will read sftp commands from file.

            -v verbose mode, It will show all logs.
            -i ignore,       It will ignore default saved user and ask for user, hostname and password.
            -s save,         It saves user credentials to cofig file for automation.
            -q quiet,        Disable asking for inputs for automation purpose.

        <some data stream> | sftp4j -u <username>
        echo "get srcfile.txt desfile.txt" | sftp4j -u sftpUser -h 127.0.0.1

        {
            get /deployment/executor/ res/executor/
            put /deployment/scripts/ res/scripts/
            get /deployment/scripts_other/10.0.62.12.txt res/file.txt
            put /deployment/conf/10.0.62.12/other.txt res/conf/other.txt
        } | sftp4j -u user -h 0.0.0.0 -p password -v

    File should contain only program specific commands like -

    get    [source remote  path] [optional destination local path]
        - download file/Dir to local system.

    put    [source local   path] [optional destination sftp  path]
        - upload file/Dir to remote system.

    rm     [path]
        - rm is a recursive function which can delete file or directory.

    mkdirs [path]
        - mkdirs is a recursive function which can create directory in recursive manner.

    read   [path]
        - print content of file to output.

    ls     [otional path]
        - ls displays files present on path. It is equivalent of 'ls -lrt' on unix os.

    cd     [path]
        - cd change directory to given path. Path can be absolute or relative.

    pwd
        - pwd prints working directory.

    bye
        - Exits from SFTP4j program.
        
    help
        - show programs manual

    here are some examples -
    ls .*zip
        => list all files having zip extention.
    get sftp/dir1 local/dir2
        result => local/dir2/dir1
    get /deployment/scripts_other/some_file.txt res
        result => res/some_file.txt
    get logs/.*txt local/logs
        result => matched files having txt extention will be downloaded.
    put /deployment/scripts/ res/scripts/
    put /deployment/conf/10.0.62.12/other.txt res/conf/other.txt
    mkdirs /1/2/3/4/5
    ---USAGE---

    ---CAUTION---

    Regular expression usage is same as java except backward slash (\) is replaced with semi-colon (;) due to conflicts with path heirarchy.
    so to user escape character inside regex user semi-colon instead of backward-slash.
    example - [ ls .*\.zip will be used as ls .*;.zip ]

    ---CAUTION---