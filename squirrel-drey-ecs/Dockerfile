FROM ubuntu:16.04

MAINTAINER Fede Diaz nordri@gmail.com

RUN apt-get update && \
    apt-get install -y openjdk-8-jdk

COPY ./distributed-algorithm-aws-0.0.1-SNAPSHOT.jar /distributed-algorithm-aws-0.0.1-SNAPSHOT.jar
COPY ./run.sh /run.sh
COPY ./hazelcast-config.xml /hazelcast-config.xml
COPY ./hazelcast-client-config.xml /hazelcast-client-config.xml

CMD [ "/run.sh" ]

