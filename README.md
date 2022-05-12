# oracle-adb
Oracle Autonomous Database proxy Container / Docker images.

**The images are compatible with `podman` and `docker`. You can use `podman` or `docker` interchangeably.**

# Supported tags and respective `Dockerfile` links

* [`19.0.0`, `latest`](https://github.com/loiclefevre/oci-oracle-adbs/blob/main/Dockerfile.1900)
* [`21.0.0`](https://github.com/loiclefevre/oci-oracle-adbs/blob/main/Dockerfile.2100)

# Quick Start

Run an Autonomous JSON Database 19c named myadb (provisioning it if it didn't exist, or restarting it if stopped). The ADMIN user
will have the ADMIN_PASSWORD password while the application user (named USER) with have USER_PASSWORD password. The IP_ADDRESS is a list of comma-separated (no space), *public* IPv4 addresses that will be able to connect to this autonomous database instance (e.g. ACLs). 

```shell
docker run -d -e REUSE=true -e DATABASE_NAME=myadb -e PROFILE_NAME=DEFAULT -e ADMIN_PASSWORD=<ADMIN user password> -e WORKLOAD_TYPE=json -e USER=<application user> -e USER_PASSWORD=<application user password> -e IP_ADDRESS=<your public IP address> loiclefevre/oracle-adb
```