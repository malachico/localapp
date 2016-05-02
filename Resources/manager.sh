#!/bin/bash
wget http://malachi-amir-bucket.s3.amazonaws.com/manager.enc
openssl -d -out manager.jar -in manager.enc -pass $$filePassword$$
java -jar manager.jar $$missionsPerWorker$$
