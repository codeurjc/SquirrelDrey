# Squirrel Drey - Kubernetes

# Introduction

This repo aims to help you to test Squirrel Drey in Kubernetes. 

# Deploying

## Prerequisites

1. Up and running Kubernetes version 1.6 or higher.

   - For development and testing, you may use Minikube
   - You must have the Kubernetes command line tool, kubectl, installed

2. Another important note would be that this document assumes some familiarity with kubectl, Kubernetes, and Docker.

## Starting

### Minikube

We're going to deploy the sample app in our cluster:

1. RBAC Authorization

Grant authorization to pods can connect to Kubernetes API

`$ kubectl apply -f hazelcast-rbac.yaml`

2. Create config maps

With the config maps we settle how each cluster member can find other members.

`$ kubectl create -f hazelcast-config-workers.yaml -f hazelcast-config-web.yaml`

3. We deploy the workers first

`$ kubectl create -f hazelcast-service-worker.yaml`

4. And then the web service

`$ kubectl create -f hazelcast-config-web.yaml`

5. You can reach the service just typing

`$ minikube service hazelcast-web`

### AWS

If you want to deploy to AWS change the web service `NodePort`by `LoadBalancer`and use the URL from the command:

`$ kubectl describe service/hazelcast-web`

to reach the app.

## Build the app

In order to dockerize the app follow those steps:

1. Compile

```
git clone https://github.com/codeurjc/SquirrelDrey.git
cd SquirrelDrey/squirrel-drey-sampleapp
mvn -DskipTests=true package
```

2. Copy the generated _jar_ into _Docker_ folder

`$ cp target/squirrel-drey-sampleapp-1.0.0.jar Docker/`

3. Build the image

`$ docker build -t <your Docker Hub account>/squirrel-drey-k8s .`

4. Push to Docker Hub

`$ docker push <your Docker Hub account>/squirrel-drey-k8s `

5. Replace this new docker image in the specs.

# Refs:

- https://github.com/codeurjc/SquirrelDrey#running-sample-applications

- https://github.com/hazelcast/hazelcast-docker/tree/master/hazelcast-kubernetes

- https://github.com/kubernetes/minikube
