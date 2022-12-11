#!/bin/sh

KS_MY_NS=otus-msa-api-gateway

kubectl create namespace $KS_MY_NS

helm install auth-db bitnami/postgresql -f ./pg/postgres-values.yaml --namespace $KS_MY_NS
# helm show values bitnami/postgresql > values.yaml
# helm list --namespace $KS_MY_NS
# helm get notes auth-db --namespace $KS_MY_NS


eval $(minikube docker-env) && docker build -t user-app:latest -f user_app/Dockerfile user_app
kubectl apply -f ./user_app/deploy.yml --namespace $KS_MY_NS

eval $(minikube docker-env) && docker build -t auth-app:latest -f auth_app/Dockerfile auth_app
kubectl apply -f ./auth_app/deploy.yml --namespace $KS_MY_NS


kubectl apply -f ./ingress.yaml --namespace $KS_MY_NS
