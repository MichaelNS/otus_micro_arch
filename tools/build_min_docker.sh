#!/bin/sh
eval $(minikube docker-env)
docker build --no-cache -t michaelns/my-minimalka:jdk17 .
