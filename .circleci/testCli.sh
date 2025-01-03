# inside supertokens downloaded zip

./install

if [[ $? -ne 0 ]]
then
    echo "cli testing failed... exiting!"
    exit 1
fi

supertokens start --port=8888

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

sed -i 's/# mysql_connection_uri:/mysql_connection_uri: "mysql:\/\/root:root@localhost:3306?rewriteBatchedStatements=true"/g' /usr/lib/supertokens/config.yaml
sed -i 's/# mongodb_connection_uri:/mongodb_connection_uri: mongodb:\/\/root:root@localhost:27017/g' /usr/lib/supertokens/config.yaml
sed -i 's/# disable_telemetry:/disable_telemetry: true/g' /usr/lib/supertokens/config.yaml

supertokens start --port=8889

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
