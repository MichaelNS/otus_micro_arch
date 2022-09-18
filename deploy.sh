#!/bin/sh
kubectl apply -f ./otus-namespace.yaml

kubectl apply -f ./metadata/deployment.yaml
kubectl apply -f ./metadata/service.yaml
kubectl apply -f ./metadata/ingress-rewrite.yaml
kubectl apply -f ./metadata/ingress.yaml

