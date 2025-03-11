# args: <length of array> <array like ["0.0", "0.1"]>
touch pluginInterfaceExactVersionsOutput
# i=0
# while [ $i -lt $1 ]; do 
#     currVersion=`echo $2 | jq ".[$i]"`
#     currVersion=`echo $currVersion | tr -d '"'`
#     i=$((i+1))
#     # now we have the current version like 0.0. 
#     # We now have to find something that matches dev-v0.0.* or v0.0.*
#     response=`curl -s -X GET \
#     "https://api.supertokens.io/0/plugin-interface/latest?password=$SUPERTOKENS_API_KEY&planType=FREE&mode=DEV&version=$currVersion" \
#     -H 'api-version: 0'`
#     if [[ `echo $response | jq .tag` == "null" ]]
#     then
#         echo $response
#         exit 1
#     fi
#     echo $response >> pluginInterfaceExactVersionsOutput
# done

echo "7.1" > pluginInterfaceExactVersionsOutput
