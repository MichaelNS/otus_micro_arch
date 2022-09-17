#!/bin/sh
eval $(minikube docker-env)
cd ./hello-py
#docker build -t hello-py:v1 .
docker build -t hello-py:0.3.0 .

helm uninstall myapp

cd ..

helm install myapp ./hello-chart
