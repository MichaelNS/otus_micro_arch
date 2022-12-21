#!/bin/sh

KS_MY_NS=otus-msa-project

kubectl delete -f deployments/ingress.yaml --namespace $KS_MY_NS

./destroy_app.sh

helm uninstall file-dev-db --namespace $KS_MY_NS
helm uninstall main-db --namespace $KS_MY_NS
kubectl delete pvc -l app.kubernetes.io/instance=file-dev-db --namespace $KS_MY_NS
kubectl delete pvc -l app.kubernetes.io/instance=main-db --namespace $KS_MY_NS

helm uninstall auth-db --namespace $KS_MY_NS
kubectl delete pvc -l app.kubernetes.io/instance=auth-db --namespace $KS_MY_NS

helm uninstall postgres-exporter-file-dev --namespace $KS_MY_NS
helm uninstall postgres-exporter-auth --namespace $KS_MY_NS

