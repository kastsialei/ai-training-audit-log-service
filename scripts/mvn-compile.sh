#!/bin/bash
export PATH=/opt/soft/apache-maven-3.9.9/bin:$PATH
export JAVA_HOME="/opt/soft/jdk-21.0.4+7"
mvn -q compile test-compile 2>&1 | tail -20