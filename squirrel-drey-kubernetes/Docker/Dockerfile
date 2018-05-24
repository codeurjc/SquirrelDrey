FROM ubuntu:16.04

MAINTAINER Fede Diaz nordri@gmail.com

RUN apt-get update && \
    apt-get install -y openjdk-8-jdk

COPY ./squirrel-drey-sampleapp-1.0.0.jar /app.jar
COPY ./run.sh /run.sh
COPY ./hazelcast.xml /hazelcast.xml
COPY ./hazelcast-client-config.xml /hazelcast-client-config.xml

CMD [ "/run.sh" ]

