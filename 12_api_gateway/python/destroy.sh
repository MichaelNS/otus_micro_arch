#!/bin/sh

KS_MY_NS=otus-msa-api-gateway

kubectl delete -f ./ingress.yaml --namespace $KS_MY_NS

kubectl delete -f ./auth_app/deploy.yml --namespace $KS_MY_NS
kubectl delete -f ./user_app/deploy.yml --namespace $KS_MY_NS

helm uninstall auth-db --namespace $KS_MY_NS
kubectl delete pvc -l app.kubernetes.io/instance=auth-db --namespace $KS_MY_NS
