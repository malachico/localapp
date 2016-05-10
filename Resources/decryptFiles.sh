#!/bin/bash

# Setting: Password (Change constant in LocalApp as well).
FILE_PASSWORD=foofoofoofoo

# Create new encrypted files.
openssl des -d -out Manager.jar -in manager.enc -k ${FILE_PASSWORD}
openssl des -d -out Worker.jar  -in worker.enc  -k ${FILE_PASSWORD}

echo "Decryption done."

