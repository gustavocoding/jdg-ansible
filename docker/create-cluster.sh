#!/usr/bin/env bash

set -e

function start-container() {
  docker run --name centos$1 --privileged -td gustavonalle/centos
  CONTAINER=$(docker ps -lq)
  docker cp ~/.ssh/id_rsa.pub "$CONTAINER":/root/.ssh/authorized_keys
  docker exec "$CONTAINER" chown root:root /root/.ssh/authorized_keys
  IP=$(docker exec "$CONTAINER" hostname -i)
  while ! ssh -oStrictHostKeyChecking=no root@$IP uname -r 2>/dev/null
  do
   echo "testing server connection"
   sleep 1;
  done
  echo "Started $IP successfully"
}

usage() {
   cat << EOF
      Usage: ./create-cluster.sh [-s size]
        -s Number of containers
        -h help
EOF
}

while getopts ":s:h" o; do
    case "${o}" in
        h) usage; exit 0;;
        s)
            s=${OPTARG}
            ;;
        *)
            usage; exit 0
            ;;
    esac
done

shift $((OPTIND-1))

if [[ -z "${s}"  ]]
then
    usage
    exit 1
fi

for ((i=0; i < s; i++))
do
	start-container $i
done

echo "Creating hosts file"
echo "[jdg]" > hosts
for c in $(docker ps -q -f name=centos)
do
  echo -e $(docker exec -it $c hostname -i) >> hosts
done

