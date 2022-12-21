#!/bin/sh

KS_MY_NS=otus-msa-project

kubectl delete -f ./auth-app/deploy.yml --namespace $KS_MY_NS
kubectl delete -f ./user-app/deploy.yml --namespace $KS_MY_NS
kubectl delete -f ./file-dev-app/deploy.yml --namespace $KS_MY_NS
