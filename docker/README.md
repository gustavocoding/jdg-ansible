## Generating test hosts using Docker

Simple image with a barebones systemd-enabled CentOS with ssh enabled, used to simulate ansible hosts.

### Building the image

```
docker build . -t gustavonalle/centos
```

### Create hosts

```
./create-cluster.sh -n 4
```
 
Hosts should be accessible with ```ssh root@172.17.0.x```.
