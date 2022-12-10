#!/bin/sh

KS_MY_NS=otus-msa-api-gateway

kubectl create namespace $KS_MY_NS

helm install auth-db bitnami/postgresql -f ./pg/postgres-values.yaml --namespace $KS_MY_NS


# ./deploy_app.sh
APP_DIR=auth-app
kubectl apply -f ./$APP_DIR/deploy.yml --namespace $KS_MY_NS
APP_DIR=user-app
kubectl apply -f ./$APP_DIR/deploy.yml --namespace $KS_MY_NS


kubectl apply -f ./ingress.yaml --namespace $KS_MY_NS
