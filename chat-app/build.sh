#!/usr/bin/env bash
# Builds both projects, runs the tests, and collects the executable JARs + config files in dist/.
set -euo pipefail
cd "$(dirname "$0")"

mvn -B clean package

rm -rf dist && mkdir -p dist
cp TCPServer/target/TCPServer.jar dist/
cp TCPClient/target/TCPClient.jar dist/
cp TCPServer/src/main/resources/server.properties dist/
cp TCPClient/src/main/resources/client.properties dist/

echo
echo "Done. Executables are in dist/:"
ls -1 dist
echo
echo "Run:  cd dist && java -jar TCPServer.jar"
echo "      cd dist && java -jar TCPClient.jar localhost 3000"
