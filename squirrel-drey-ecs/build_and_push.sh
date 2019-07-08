#!/bin/bash 
set -eu -o pipefail

cp ../squirrel-drey-sampleapp/target/squirrel-drey-sampleapp*.jar .

docker build -t 093555767787.dkr.ecr.eu-west-1.amazonaws.com/squirreldrey:sampleapp .
docker push 093555767787.dkr.ecr.eu-west-1.amazonaws.com/squirreldrey:sampleapp
