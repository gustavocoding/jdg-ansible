## JBoss Data Grid Deployment playbook



### Requirements

* Ansible
* Linux
* Pre-download JDG server zip to ```files/server```
* Pre-download agent zip to ```files/agent```

### Configuration

Change the file ```group_vars``` for the java version, server and agent zip and JVM properties as needed. 

### Run locally on docker

Create some local docker containers using the script 

    docker/create-cluster.sh -s 3
    
The script will output the ips of the container, grab and put them in the ```hosts``` file and run:

	export ANSIBLE_HOST_KEY_CHECKING=false;ansible-playbook -u root -i hosts site.yml

The playbook will install and start all the servers.

#### Run command in all servers

To execute a shell command in all nodes of the cluster, for e.g. ```pgrep -f jboss```:

    ansible -u root -i hosts all -a "pgrep -f jboss" 
	
### Run on AWS

//TODO
