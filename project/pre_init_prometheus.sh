#!/bin/sh

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

kubectl create namespace $KS_MY_NS
helm install prom prometheus-community/kube-prometheus-stack -f deployments/prometheus.yaml --atomic --namespace $KS_MY_NS
