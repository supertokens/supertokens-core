case $1 in
    mysql)
        (cd / && ./runMySQL.sh)
        mysql -u root --password=root -e "CREATE DATABASE auth_session;"
        ;;
esac