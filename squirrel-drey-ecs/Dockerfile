FROM ubuntu:16.04

LABEL maintainer Fede Diaz nordri@gmail.com

RUN apt-get update && \
    apt-get install -y openjdk-8-jdk

COPY ./squirrel-drey-sampleapp*.jar /app.jar
COPY ./run.sh /run.sh
COPY ./hazelcast-config.xml /hazelcast-config.xml

CMD [ "/run.sh" ]

