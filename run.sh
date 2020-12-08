#!/usr/bin/env bash
set -x
set -e
function run() {
  for THREADS in 10 50 500
  do
      ansible-playbook -u ec2-user --extra-vars "stress_threads=$THREADS duration_min=5"  stress.yml
  done
}

# Run with configured G1 settings
ansible-playbook --user ec2-user site-ec2.yml
run

# Run with default G1 setting
ansible-playbook --user ec2-user --extra-vars "gc_opts='-XX:+UseG1GC'" site-ec2.yml
run

# Run with CMS
ansible-playbook --user ec2-user --extra-vars "gc_opts='-XX:+UseLargePages -XX:+UseConcMarkSweepGC -XX:+UseParNewGC'" site-ec2.yml
run
