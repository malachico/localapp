#!/bin/bash  
wget http://repo1.maven.org/maven2/edu/stanford/nlp/stanford-corenlp/3.3.0/stanford-corenlp-3.3.0-models.jar  
wget http://repo1.maven.org/maven2/com/googlecode/efficient-java-matrix-library/ejml/0.23/ejml-0.23.jar  
wget http://repo1.maven.org/maven2/edu/stanford/nlp/stanford-corenlp/3.3.0/stanford-corenlp-3.3.0.jar  
wget http://central.maven.org/maven2/de/jollyday/jollyday/0.4.7/jollyday-0.4.7.jar  
wget http://malachi-amir-bucket.s3.amazonaws.com/worker.enc
openssl -d -out worker.jar -in worker.enc -pass moo64
java -Xms128m -Xmx768M -cp .:worker.jar:stanford-corenlp-3.3.0.jar:stanford-corenlp-3.3.0-models.jar:ejml-0.23.jar:jollyday-0.4.7.jar Analyzer
touch done
