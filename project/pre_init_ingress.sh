#!/bin/sh

# install ingress
kubectl create namespace m
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx/
helm repo update
helm install nginx ingress-nginx/ingress-nginx --namespace m -f deployments/nginx-ingress.yaml

# helm delete nginx --namespace m
