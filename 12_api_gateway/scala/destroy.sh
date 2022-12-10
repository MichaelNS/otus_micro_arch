#!/bin/sh

KS_MY_NS=otus-msa-api-gateway

kubectl delete -f ./ingress.yaml --namespace $KS_MY_NS

./destroy_app.sh

helm uninstall auth-db --namespace $KS_MY_NS
kubectl delete pvc -l app.kubernetes.io/instance=auth-db --namespace $KS_MY_NS

kubectl delete namespace $KS_MY_NS
