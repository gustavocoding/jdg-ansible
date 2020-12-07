## Generating test hosts using Docker

Simple image with a barebones systemd-enabled CentOS with ssh enabled, used to simulate ansible hosts.

### Building the image

```
docker build . -t gustavonalle/centos
```
