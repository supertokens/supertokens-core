# inside supertokens downloaded zip

./install

if [[ $? -ne 0 ]]
then
    echo "cli testing failed... exiting!"
    exit 1
fi

sed -i 's/# refresh_api_path:/refresh_api_path: \/refresh/g' /usr/lib/supertokens/config.yaml
sed -i 's/# cookie_domain:/cookie_domain: supertokens.io/g' /usr/lib/supertokens/config.yaml
sed -i 's/# mysql_user:/mysql_user: root/g' /usr/lib/supertokens/config.yaml
sed -i 's/# mysql_password:/mysql_password: root/g' /usr/lib/supertokens/config.yaml
sed -i 's/# mongodb_connection_uri:/mongodb_connection_uri: mongodb:\/\/root:root@localhost:27017/g' /usr/lib/supertokens/config.yaml

supertokens start dev --port=8888

if [[ $? -ne 0 ]]
then
    echo "cli testing failed... exiting!"
    exit 1
fi

supertokens list

if [[ $? -ne 0 ]]
then
    echo "cli testing failed... exiting!"
    exit 1
fi

supertokens start dev --port=8889

supertokens list

if [[ $? -ne 0 ]]
then
    echo "cli testing failed... exiting!"
    exit 1
fi

curl http://localhost:8889/hello

if [[ $? -ne 0 ]]
then
    echo "cli testing failed... exiting!"
    exit 1
fi

curl http://localhost:8888/hello

if [[ $? -ne 0 ]]
then
    echo "cli testing failed... exiting!"
    exit 1
fi

supertokens stop

if [[ $? -ne 0 ]]
then
    echo "cli testing failed... exiting!"
    exit 1
fi

supertokens uninstall

if [[ $? -ne 0 ]]
then
    echo "cli testing failed... exiting!"
    exit 1
fi
