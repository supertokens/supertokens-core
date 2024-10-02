function cleanup {
    if test -f "pluginInterfaceExactVersionsOutput"; then
        rm pluginInterfaceExactVersionsOutput 
    fi
}

trap cleanup EXIT
cleanup

pluginToTest=$1

pinnedDBJson=$(curl -s -X GET \
  'https://api.supertokens.io/0/plugin/pinned?planType=FREE' \
  -H 'api-version: 0')
pinnedDBLength=$(echo "$pinnedDBJson" | jq ".plugins | length")
pinnedDBArray=$(echo "$pinnedDBJson" | jq ".plugins")
echo "got pinned dbs..."

pluginInterfaceJson=$(cat ../pluginInterfaceSupported.json)
pluginInterfaceLength=$(echo "$pluginInterfaceJson" | jq ".versions | length")
pluginInterfaceArray=$(echo "$pluginInterfaceJson" | jq ".versions")
echo "got plugin interface relations"

coreDriverJson=$(cat ../coreDriverInterfaceSupported.json)
coreDriverArray=$(echo "$coreDriverJson" | jq ".versions")
echo "got core driver relations"

./getPluginInterfaceExactVersions.sh "$pluginInterfaceLength" "$pluginInterfaceArray"

if [[ $? -ne 0 ]]
then
    echo "all plugin interfaces found... failed. exiting!"
	exit 1
else
    echo "all plugin interfaces found..."
fi

# get core version
coreVersion=$(cat ../build.gradle | grep -e "version =" -e "version=")
while IFS='"' read -ra ADDR; do
    counter=0
    for i in "${ADDR[@]}"; do
        if [ $counter == 1 ]
        then
            coreVersion=$i
        fi
        counter=$(($counter+1))
    done
done <<< "$coreVersion"

responseStatus=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
  https://api.supertokens.io/0/core \
  -H 'Content-Type: application/json' \
  -H 'api-version: 0' \
  -d "{
	\"password\": \"$SUPERTOKENS_API_KEY\",
	\"planType\":\"FREE\",
	\"version\":\"$coreVersion\",
	\"pluginInterfaces\": $pluginInterfaceArray,
	\"coreDriverInterfaces\": $coreDriverArray
}")
if [ "$responseStatus" -ne "200" ]
then
    echo "failed core PUT API status code: $responseStatus. Exiting!"
	exit 1
fi

someTestsRan=false
while read -u 10 line
do
    if [[ $line = "" ]]; then
        continue
    fi
    i=0
    currTag=$(echo "$line" | jq .tag)
    currTag=$(echo "$currTag" | tr -d '"')

    currVersion=$(echo "$line" | jq .version)
    currVersion=$(echo "$currVersion" | tr -d '"')
    piX=$(cut -d'.' -f1 <<<"$currVersion")
    piY=$(cut -d'.' -f2 <<<"$currVersion")
    piVersion="$piX.$piY"
    
    while [ $i -lt "$pinnedDBLength" ]; do
        someTestsRan=true
        currPinnedDb=$(echo "$pinnedDBArray" | jq ".[$i]")
        currPinnedDb=$(echo "$currPinnedDb" | tr -d '"')

        i=$((i+1))

        if [[ $currPinnedDb == $pluginToTest ]]
        then

          echo ""
          echo ""
          echo ""
          echo ""
          echo ""
          echo "===== testing $currPinnedDb with plugin-interface $currVersion ====="
          echo ""
          echo ""
          echo ""
          echo ""
          echo ""

          if [[ $currPinnedDb == "sqlite" ]]
          then
            # shellcheck disable=SC2034
            continue=1
          else
            response=$(curl -s -X GET \
            "https://api.supertokens.io/0/plugin-interface/dependency/plugin/latest?password=$SUPERTOKENS_API_KEY&planType=FREE&mode=DEV&version=$piVersion&pluginName=$currPinnedDb" \
            -H 'api-version: 0')
            if [[ $(echo "$response" | jq .plugin) == "null" ]]
            then
                echo "fetching latest X.Y version for $currPinnedDb given plugin-interface X.Y version: $piVersion gave response: $response"
                exit 1
            fi
            pinnedDbVersionX2=$(echo $response | jq .plugin | tr -d '"')

            response=$(curl -s -X GET \
            "https://api.supertokens.io/0/plugin/latest?password=$SUPERTOKENS_API_KEY&planType=FREE&mode=DEV&version=$pinnedDbVersionX2&name=$currPinnedDb" \
            -H 'api-version: 0')
            if [[ $(echo "$response" | jq .tag) == "null" ]]
            then
                echo "fetching latest X.Y.Z version for $currPinnedDb, X.Y version: $pinnedDbVersionX2 gave response: $response"
                exit 1
            fi
            pinnedDbVersionTag=$(echo "$response" | jq .tag | tr -d '"')
            pinnedDbVersion=$(echo "$response" | jq .version | tr -d '"')
            ./startDb.sh "$currPinnedDb"
          fi

          cd ../../
          git clone git@github.com:supertokens/supertokens-root.git
          cd supertokens-root
          rm gradle.properties

          update-alternatives --install "/usr/bin/java" "java" "/usr/java/jdk-15.0.1/bin/java" 2
          update-alternatives --install "/usr/bin/javac" "javac" "/usr/java/jdk-15.0.1/bin/javac" 2

          coreX=$(cut -d'.' -f1 <<<"$coreVersion")
          coreY=$(cut -d'.' -f2 <<<"$coreVersion")
          if [[ $currPinnedDb == "sqlite" ]]
          then
            echo -e "core,$coreX.$coreY\nplugin-interface,$piVersion" > modules.txt
          else
            echo -e "core,$coreX.$coreY\nplugin-interface,$piVersion\n$currPinnedDb-plugin,$pinnedDbVersionX2" > modules.txt
          fi
          ./loadModules
          cd supertokens-core
          git checkout dev-v$coreVersion
          cd ../supertokens-plugin-interface
          git checkout $currTag
          if [[ $currPinnedDb == "sqlite" ]]
          then
            # shellcheck disable=SC2034
            continue=1
          else
            cd ../supertokens-$currPinnedDb-plugin
            git checkout $pinnedDbVersionTag
          fi
          cd ../
          echo $SUPERTOKENS_API_KEY > apiPassword
          ./startTestingEnv --cicd

          if [[ $? -ne 0 ]]
          then
              echo ""
              echo ""
              echo ""
              echo ""
              echo ""
              echo "===== testing $currPinnedDb with plugin-interface $currVersion FAILED ====="
              echo ""
              echo ""
              echo ""
              echo ""
              echo ""

              cat logs/*
              cd ../project/
              echo "test failed... exiting!"
              exit 1
          fi

          echo ""
          echo ""
          echo ""
          echo ""
          echo ""
          echo "===== testing $currPinnedDb with plugin-interface $currVersion SUCCEEDED ====="
          echo ""
          echo ""
          echo ""
          echo ""
          echo ""

          cd ../
          rm -rf supertokens-root

          if [[ $currPinnedDb == "sqlite" ]]
          then
            # shellcheck disable=SC2034
            continue=1
          else
            curl -o supertokens.zip -s -X GET \
            "https://api.supertokens.io/0/app/download?pluginName=$currPinnedDb&os=linux&mode=DEV&binary=FREE&targetCore=$coreVersion&targetPlugin=$pinnedDbVersion" \
            -H 'api-version: 0'
            unzip supertokens.zip -d .
            rm supertokens.zip
            cd supertokens
            ../project/.circleci/testCli.sh
            if [[ $? -ne 0 ]]
            then
                echo "cli testing failed... exiting!"
                exit 1
            fi
            cd ../
          fi

          rm -rf supertokens
          cd project/.circleci
          if [[ $currPinnedDb == "sqlite" ]]
          then
            # shellcheck disable=SC2034
            continue=1
          else
            ./stopDb.sh $currPinnedDb
          fi

        fi

    done
done 10<pluginInterfaceExactVersionsOutput

if [[ $someTestsRan = "true" ]]
then
    echo "tests ran successfully"
else
    echo "no test ran"
    exit 1
fi
