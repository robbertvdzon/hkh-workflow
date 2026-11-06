#!/bin/sh
# Werkt frontend/openapi.json bij met wat de backend nu publiceert. Daarna maakt de build er de TypeScript-types uit.
# De frontend wordt hierbij niet gebouwd: die past misschien nog niet bij de nieuwe API. Vraagt Docker.
set -e
cd "$(dirname "$0")/.."
mvn -q test -Dskip.npm -Dskip.installnodenpm -Dtest=CucumberIntegrationTest -Dcucumber.filter.tags=@openapi -Dopenapi.update=true
