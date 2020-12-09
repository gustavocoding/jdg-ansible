#!/usr/bin/env bash
set -e
set -x

DURATION_MIN=10

function run() {
  for THREADS in 10 100 500
  do
      ansible-playbook -u ec2-user --extra-vars "stress_threads=$THREADS duration_min=$DURATION_MIN"  stress.yml
      ansible-playbook -u ec2-user -l loader site-ec2.yml
  done
}

# Run with configured G1 settings
ansible-playbook --user ec2-user site-ec2.yml
run

# Run with default G1 setting
ansible-playbook --user ec2-user --extra-vars "gc_opts='-XX:+UseG1GC'" site-ec2.yml
run

# Run with smaller pauses
ansible-playbook --user ec2-user --extra-vars "gc_opts='-XX:+UseG1GC -XX:MaxGCPauseMillis=50'" site-ec2.yml
run

# Run with CMS
ansible-playbook --user ec2-user --extra-vars "gc_opts='-XX:+UseLargePages -XX:+UseConcMarkSweepGC -XX:+UseParNewGC'" site-ec2.yml
run
