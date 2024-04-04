case $1 in
    mysql)
        (cd / && ./runMySQL.sh)
        mysql -u root --password=root -e "CREATE DATABASE supertokens;"
        ;;
    postgresql)
        /etc/init.d/postgresql start
        sudo -u postgres psql --command "CREATE USER root WITH SUPERUSER PASSWORD 'root';"
        createdb
        psql -c "create database supertokens;"
esac