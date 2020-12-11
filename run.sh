#!/usr/bin/env bash
set -e
set -x

# For AWS test
PARAMS="-u ec2-user"
DURATION_MIN=10
SITE="site-ec2.yml"

# For Local Cluster
#PARAMS="-i hosts -u root"
#DURATION_MIN=1
#SITE="site-local.yml"

function run() {
  for THREADS in 10 50 500
  do
       ansible-playbook -v $PARAMS --extra-vars "stress_threads=$THREADS duration_min=$DURATION_MIN" stress.yml
       ansible-playbook -v $PARAMS -l loader $SITE
  done
}

# Run with configured G1 settings
ansible-playbook -v $PARAMS $SITE
run

# Run with default G1 setting
ansible-playbook -v $PARAMS --extra-vars "gc_opts='-XX:+UseG1GC'" $SITE
run

# Run with smaller pauses
ansible-playbook -v $PARAMS --extra-vars "gc_opts='-XX:+UseG1GC -XX:MaxGCPauseMillis=50'" $SITE
run

# Run with CMS
ansible-playbook -v $PARAMS --extra-vars "gc_opts='-XX:+UseLargePages -XX:+UseConcMarkSweepGC -XX:+UseParNewGC'" $SITE
run
