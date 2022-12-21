#!/bin/sh

helm delete prom --namespace $KS_MY_NS

kubectl delete namespace $KS_MY_NS
