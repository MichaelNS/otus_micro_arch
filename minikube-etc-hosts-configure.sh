#!/bin/sh
minikube addons enable ingress
echo Appending into /etc/hosts
echo "$(minikube ip) arch.homework" | sudo tee -a /etc/hosts

