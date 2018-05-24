#!/bin/bash

if [ -z "$SERVICE_NAME" ]; then echo "No SERVICE_NAME specified"; exit 1; fi
if [ -z "$SERVICE_LABEL_NAME" ]; then echo "No SERVICE_LABEL_NAME specified"; exit 1; fi
if [ -z "$SERVICE_LABEL_VALUE" ]; then echo "No SERVICE_LABEL_VALUE specified"; exit 1; fi
if [ -z "$NAMESPACE" ]; then echo "NAMESPACE"; exit 1; fi



if [ "$TYPE" == "web" ]; then
  sed -i "s/SERVICE_NAME/$SERVICE_NAME/" hazelcast-client-config.xml
  sed -i "s/SERVICE_LABEL_NAME/$SERVICE_LABEL_NAME/" hazelcast-client-config.xml
  sed -i "s/SERVICE_LABEL_VALUE/$SERVICE_LABEL_VALUE/" hazelcast-client-config.xml
  sed -i "s/NAMESPACE/$NAMESPACE/" hazelcast-client-config.xml
  java -Dworker=false -Dhazelcast-client-config=/hazelcast-client-config.xml -Daws=false -jar /app.jar
else
  sed -i "s/SERVICE_NAME/$SERVICE_NAME/" hazelcast.xml
  sed -i "s/SERVICE_LABEL_NAME/$SERVICE_LABEL_NAME/" hazelcast.xml
  sed -i "s/SERVICE_LABEL_VALUE/$SERVICE_LABEL_VALUE/" hazelcast.xml
  sed -i "s/NAMESPACE/$NAMESPACE/" hazelcast.xml
  java -Dworker=true -Dhazelcast-config=/hazelcast.xml -Dmode=PRIORITY -jar /app.jar
fi


