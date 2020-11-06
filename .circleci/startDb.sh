case $1 in
    mysql)
        (cd / && ./runMySQL.sh)
        mysql -u root --password=root -e "CREATE DATABASE supertokens;"
        ;;
esac