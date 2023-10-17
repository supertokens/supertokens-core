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

echo "calling /core PATCH to make testing passed"
responseStatus=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH \
    https://api.supertokens.io/0/core \
    -H 'Content-Type: application/json' \
    -H 'api-version: 0' \
    -d "{
        \"password\": \"$SUPERTOKENS_API_KEY\",
        \"planType\":\"FREE\",
        \"version\":\"$coreVersion\",
        \"testPassed\": true
    }")
if [ "$responseStatus" -ne "200" ]
then
    echo "patch api failed"
    exit 1
fi
