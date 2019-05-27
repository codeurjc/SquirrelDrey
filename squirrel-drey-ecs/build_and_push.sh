#!/bin/bash 
set -eu -o pipefail

cp ../squirrel-drey-sampleapp/target/squirrel-drey-sampleapp*.jar .

docker build -t 849201093595.dkr.ecr.eu-west-1.amazonaws.com/squirreldrey:latest .
docker push 849201093595.dkr.ecr.eu-west-1.amazonaws.com/squirreldrey:latest
