case $1 in
    mysql)
        service mysql stop
        ;;
    postgresql)
        service postgresql stop
        ;;
esac