#!/bin/bash

echo "Worker started, waiting for jobs..."

while true
do
    job=$(redis-cli BRPOP jobs 1)

    if [ ! -z "$result" ]; then
        job_name=$(echo "$result" | tail -n 1)
        echo "Processing $job_name"
        sleep 2
        echo "$job_name completed"
    fi
done