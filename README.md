## JBoss Data Grid Deployment playbook

### Description

This playbook installs a JDG cluster, app dynamic agent and populates it with sample data. 

### Requirements

* Ansible
* Linux (or MacOS when using ```docker-machine```)
* Pre-download JDG server zip to ```files/server```
* Pre-download agent zip to ```files/agent```

### Configuration

Change the file ```group_vars/all``` to customize the cluster creation. The following properties are available:

* ```java_version```: The java version to use, default is ```8.0.275.open-adpt```. For possible values consult the output of the command ```sdk list java``` from [sdkman](https://sdkman.io/) (column Identifier)

* ```server_zip```: The server zip to use, should be pre-copied to ```files/servers```

* ```custom_server_config```: An optional server config to use. The default is to use ```clustered.xml``` from the servers zip or ```cloud.xml``` for EC2. To use custom config, uncomment this property and make sure the file is under ```servers/overlay```, in the same directory structure as the server.

* ```agent_zip```: The agent zip to use, should be pre-copied to ```files/agents```
  
The properties ```gc_opts```, ```agent_opts``` and ```fr_opts``` will be added together to compose
the ```JAVA_OPTS``` used to start the server, and contain respectively, the garbage collector options, the agent options, and the flight recording options.
  
The following properties controls the initial data loading in the cluster:

* ```cache_name```: The name of the cache to load data.
  
* ```entries```: The number of entries to load in the cache. Keys are String (ids) and values are random phrases with words picked from the ```loader/words.txt``` file.

* ```phrase```: How many words the generated phrases will contain.

* ```protocol```: The HotRod protocol used to populate the cluster. 

* ```batch```: The size of the ```putAll``` requests to populate the cache.

### Run locally on docker

Create some local docker containers using the script: 

    docker/create-cluster.sh -s 3
    
The script will write a file ```hosts``` with the ips of the containers. To run the playbook:

	export ANSIBLE_HOST_KEY_CHECKING=false; \
	ansible-playbook -u root -i hosts site.yml

The playbook will install and start all the servers.

#### Run command in all servers

To execute a shell command in all nodes of the cluster, for e.g. ```pgrep -f jboss```:

    ansible -u root -i hosts all -a "pgrep -f jboss" 
    
Get the number of entries in each server:

    ansible -u root -i hosts jdg -a "/opt/jdg/server/bin/cli.sh -c /subsystem=datagrid-infinispan/cache-container=clustered/distributed-cache=default:read-attribute(name=number-of-entries)"
	
Get the number of members in the cluster:

     ansible -u root -i hosts jdg -a "/opt/jdg/server/bin/cli.sh -c /subsystem=datagrid-infinispan/cache-container=clustered:read-attribute(name=members)"	
	
### Run on AWS

#### Preparation

##### Install python libs

    pip install boto

##### Export AWS credentials

    export AWS_ACCESS_KEY_ID='xxxx'
    export AWS_SECRET_ACCESS_KEY='xxxx'

### Change config

The file ```group_vars/all``` contains several properties for AWS provision:

* ```cluster_size```: Number of JDG nodes to provision

* ```region```: AWS region, e.g., ```eu-west-2```. Check [here](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.RegionsAndAvailabilityZones.html) for a list of valid ids

* ```security_group```: An existing security group with SSH inbound enabled, and unrestricted access between instances in the internal subnet. 

* ```instance_type```: Check [here](https://aws.amazon.com/ec2/instance-types/) for available instances

* ```image```: The image to run

* ```subnet```: The subnet id. Check the AWS console.

#### Provisioning

    ansible-playbook --user ec2-user site-ec2.yml
    
#### Terminating resources

    ansible-playbook --user ec2-user shutdown-ec2.yml
   
#### Executing command in JDG instances:

Local entries per node in the ```default``` cache: 

    ansible -u ec2-user  jdg -a "/opt/jdg/server/bin/cli.sh -c /subsystem=datagrid-infinispan/cache-container=clustered/distributed-cache=default:read-attribute(name=number-of-entries)"
    
Cluster view per node:

    ansible -u ec2-user  jdg -a "/opt/jdg/server/bin/cli.sh -c /subsystem=datagrid-infinispan/cache-container=clustered:read-attribute(name=members)"   

Kill the server in all nodes:
    
    ansible -u ec2-user jdg -a "pkill -f jboss-modules.jar"
