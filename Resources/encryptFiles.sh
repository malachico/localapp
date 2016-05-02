#!/bin/bash

# Setting: Password (Change constant in LocalApp as well).
FILE_PASSWORD=foofoofoofoo

# Remove old files.
rm *.enc

# Create new encrypted files.
openssl des -e -in Manager.jar -out manager.enc -k ${FILE_PASSWORD}
openssl des -e -in Worker.jar  -out worker.enc  -k ${FILE_PASSWORD}

echo 
echo "Encryption done."

