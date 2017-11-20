#!/bin/bash

if [ -z "$IAM_ROLE" ]; then echo "No IAM_ROLE specified"; exit 1; fi
if [ -z "$REGION" ]; then echo "No REGION specified"; exit 1; fi
if [ -z "$SECURITY_GROUP_NAME" ]; then echo "No SECURITY_GROUP_NAME specified"; exit 1; fi
if [ -z "$TAG_KEY" ]; then echo "No TAG_KEY specified"; exit 1; fi
if [ -z "$TAG_VALUE" ]; then echo "No TAG_VALUE specified"; exit 1; fi
if [ -z "$HZ_PORT" ]; then echo "No HZ_PORT specified"; exit 1; fi

if [ -z "$INTERFACE" ]; then echo "No INTERFACE specified"; exit 1; fi

if [ "$TYPE" == "web" ]; then
  sed -i "s/IAM_ROLE/$IAM_ROLE/" hazelcast-client-config.xml
  sed -i "s/REGION/$REGION/" hazelcast-client-config.xml
  sed -i "s/SECURITY_GROUP_NAME/$SECURITY_GROUP_NAME/" hazelcast-client-config.xml
  sed -i "s/TAG_KEY/$TAG_KEY/" hazelcast-client-config.xml
  sed -i "s/TAG_VALUE/$TAG_VALUE/" hazelcast-client-config.xml
  sed -i "s/HZ_PORT/$HZ_PORT/" hazelcast-client-config.xml

  java -Dworker=false -Dhazelcast-client-config=/hazelcast-client-config.xml -Daws=true -jar /distributed-algorithm-aws-0.0.1-SNAPSHOT.jar
else
  sed -i "s/IAM_ROLE/$IAM_ROLE/" hazelcast-config.xml
  sed -i "s/REGION/$REGION/" hazelcast-config.xml
  sed -i "s/SECURITY_GROUP_NAME/$SECURITY_GROUP_NAME/" hazelcast-config.xml
  sed -i "s/TAG_KEY/$TAG_KEY/" hazelcast-config.xml
  sed -i "s/TAG_VALUE/$TAG_VALUE/" hazelcast-config.xml
  sed -i "s/HZ_PORT/$HZ_PORT/" hazelcast-config.xml
  sed -i "s/INTERFACE/$INTERFACE/" hazelcast-config.xml

  java -Dworker=true -Dhazelcast-config=/hazelcast-config.xml -Dmode=PRIORITY -jar /distributed-algorithm-aws-0.0.1-SNAPSHOT.jar
fi


