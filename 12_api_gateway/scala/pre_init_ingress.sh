#!/bin/sh

# install ingress
kubectl create namespace m
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx/
helm repo update
helm install nginx ingress-nginx/ingress-nginx --namespace m -f nginx-ingress.yaml

# helm install --version "3.35.0" --namespace $KS_MY_NS -f nginx-config.yaml ingress-nginx ingress-nginx/ingress-nginx
# helm install --version "3.35.0" --namespace otus-msa-api-gateway -f nginx-config.yaml ingress-nginx ingress-nginx/ingress-nginx
# helm install --version "3.35.0" -n nginx-ingress -f apigw/nginx-ingress/nginx.yaml  ingress-nginx ingress-nginx/ingress-nginx
