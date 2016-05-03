#!/bin/bash
cd /home/ec2-user/
wget http://malachi-amir-bucket.s3.amazonaws.com/manager.enc
openssl des -d -out manager.jar -in manager.enc -k $$filePassword$$
java -jar manager.jar $$missionsPerWorker$$ $$filePassword$$
