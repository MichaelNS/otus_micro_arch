#!/bin/sh

KS_MY_NS=otus-msa-project

kubectl create namespace $KS_MY_NS

# helm install prom prometheus-community/kube-prometheus-stack -f deployments/prometheus.yaml --atomic --namespace $KS_MY_NS

helm install auth-db bitnami/postgresql -f ./pg/pg-values-auth-db.yaml --namespace $KS_MY_NS
helm install main-db bitnami/postgresql -f ./pg/pg-values-main-db.yaml --namespace $KS_MY_NS
helm install file-dev-db bitnami/postgresql -f ./pg/pg-values-file-dev-db.yaml --namespace $KS_MY_NS

# kubectl apply -f ./ingress.yaml --namespace $KS_MY_NS

helm install postgres-exporter-file-dev prometheus-community/prometheus-postgres-exporter -f deployments/postgresql-exporter-file-dev.yaml --namespace $KS_MY_NS
helm install postgres-exporter-auth prometheus-community/prometheus-postgres-exporter -f deployments/postgresql-exporter-auth.yaml --namespace $KS_MY_NS


# ./deploy_app.sh
APP_DIR=auth-app
kubectl apply -f ./$APP_DIR/deploy.yml --namespace $KS_MY_NS

APP_DIR=user-app
kubectl apply -f ./$APP_DIR/deploy.yml --namespace $KS_MY_NS

APP_DIR=file-dev-app
kubectl apply -f ./$APP_DIR/deploy.yml --namespace $KS_MY_NS

kubectl apply -f deployments/ingress.yaml --namespace $KS_MY_NS
