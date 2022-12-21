#!/bin/sh

KS_MY_NS=otus-msa-project

APP_DIR=auth-app
# ./build_play_app.sh
./build_play_app.sh $APP_DIR
if [ $? -eq  1 ] ; then
  echo "STOP"
  exit 1
fi

kubectl apply -f ./$APP_DIR/deploy.yml --namespace $KS_MY_NS
# kubectl apply -f ./auth-app/deploy.yml --namespace $KS_MY_NS
# --------------------------------

APP_DIR=user-app
./build_play_app.sh $APP_DIR
if [ $? -eq  1 ] ; then
  echo "STOP"
  exit 1
fi

kubectl apply -f ./$APP_DIR/deploy.yml --namespace $KS_MY_NS
# --------------------------------


APP_DIR=file-dev-app
./build_play_app.sh $APP_DIR
if [ $? -eq  1 ] ; then
  echo "STOP"
  exit 1
fi

kubectl apply -f ./$APP_DIR/deploy.yml --namespace $KS_MY_NS
# kubectl apply -f ./file-dev-app/deploy.yml --namespace $KS_MY_NS
# --------------------------------

