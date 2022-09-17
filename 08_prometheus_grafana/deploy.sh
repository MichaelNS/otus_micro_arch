#!/bin/sh

# https://github.com/helm/charts/tree/master/stable

# https://github.com/prometheus-community/helm-charts/tree/main/charts/kube-prometheus-stack
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install prom prometheus-community/kube-prometheus-stack -f prometheus.yaml --atomic

#  https://github.com/kubernetes/ingress-nginx/tree/main/charts/ingress-nginx
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install nginx ingress-nginx/ingress-nginx -f nginx-ingress.yaml --atomic

#helm install myapp ./hello-chart
helm upgrade --install myapp ./hello-chart

#helm upgrade --install -n otus-msa-hw3 otus-msa-hw3 k8s/chart
