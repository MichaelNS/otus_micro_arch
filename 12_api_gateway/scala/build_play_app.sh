#!/bin/sh

echo "------------------------------------------------------------------------------------------------------------------------"
echo "---START build play app"
echo "directory: $1"

if ! [ -d "$1" ] ; then
  echo "parameter 'name directory' is empty"
  exit 1
fi

cd ./$1
echo "cd directory:"
pwd

echo "start - sbt dist:"
sbt dist



# package
echo "start - package:"
set -x
rm -r svc
unzip -d svc target/universal/$1*.zip
mv svc/*/* svc/

if ! test -f "./svc/bin/$1"; then
    echo "ERROR - $(pwd)/svc/bin/$1 NOT exists."
    exit 1
fi

rm svc/bin/*.bat
mv svc/bin/$1 svc/bin/start



# docker_build
echo "start - docker_build:"
eval $(minikube docker-env)
docker build -t michaelns/$1:v1 -f Dockerfile .
# docker build --no-cache -t $1:v1 -f Dockerfile .

# clean dist
rm -r target/universal/
rm -r svc

echo "---END build play app"
