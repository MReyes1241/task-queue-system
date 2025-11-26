#!/bin/bash

for i in {1..10}
do
    redis-cli LPUSH  jobs "job_$i"
    echo "Added job_$i to the queue"
    sleep 0.5
done