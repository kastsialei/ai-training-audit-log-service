#!/bin/bash
export PATH=/opt/soft/apache-maven-3.9.9/bin:$PATH
export JAVA_HOME="/opt/soft/jdk-21.0.4+7"
mvn -q spotless:apply 2>&1 | tail -10 && echo --- && mvn verify 2>&1 | grep -E "Tests run:|BUILD|FAIL|ERROR.*Test" | tail -20