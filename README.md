# Consonance

[![Build Status](https://travis-ci.org/Consonance/consonance.svg?branch=develop_2)](https://travis-ci.org/Consonance/Consonance)
[![Coverage Status](https://coveralls.io/repos/Consonance/consonance/badge.svg?branch=develop_2)](https://coveralls.io/r/Consonance/consonance?branch=develop)

### Components

There are the following main components that are visible to the end-user. Each component usually contains just the unit tests for each component in isolation:

* consonance-arch: contains the main consonance daemons that handle things like provisioning VMs, running jobs, etc. 
* consonance-client: contains the client classes that will form the basis for the command line client
* consonance-webservice: containers the webservice which is built as a facade between the client and our daemons
* consonance-reporting: contains reporting utilities for those running the daemons

There are the following support components:

* consonance-common: contains classes common to both the client and server (just testing utilities)
* consonance-server-common: contains classes common to the daemons and the webservice
* swagger-java-client: contains classes auto-generated by swagger-codegen to support the client
* consonance-integration-testing: contains integration tests that test the whole system


### Release Process

Use the standard mvn release:prepare and mvn release:perform procedures with maven 3

### Updating Auto-Generated Code

#### Schema

The schema is generated by dumping the auto-generated schema from Dropwizard/Hibernate. 

    pg_dump queue_status --schema-only &> schema.sql
    mv schema.sql consonance-arch/sql

#### Swagger Client

The basis for the swagger client is auto-generated from Dropwizard. While you have the web service running, create the classes here using the [https://github.com/swagger-api/swagger-codegen/blob/master/README.md](swagger-codegen) project.                    
                                                                                                                                                      
    git clone https://github.com/swagger-api/swagger-codegen.git
    cd swagger-codegen
    ./run-in-docker.sh generate   -i http://10.0.3.18:8080/swagger.json   -l java   -o test_swagger_output --library jersey2                                          
    cp -R  test_swagger_output/src ~/consonance/swagger-java-client/
                                                                                                                                                      
We modified the pom.xml to be compatible with our parent pom, but otherwise no files in here should be manually changed in case we wish to automatically regenerate the classes above based on changes in the web service.     

## Usage

Usage of this system for both end-users and administrators requires Ubuntu 14.04+ and [Java 8](http://www.webupd8.org/2012/09/install-oracle-java-8-in-ubuntu-via-ppa.html). 

### End-User

Download the Consonance Bash script from [github](https://github.com/Consonance/consonance/releases). 
Put it on your PATH and run it to automatically download the client-jar (requirement of Java 8) and set up a config file.

Basic documentation is available through the command-line. 

    ubuntu@consonance-user:~$ consonance

    Usage: consonance [<flag>]
      consonance <command> [--help]

    Commands:
      run           Schedule a job
      status        Get the status of a job

    Flags:
      --help        Print help out
      --debug       Print debugging information
      --version     Print Consonance's version
      --metadata    Print metadata environment

Examples of useful commands are:

1. Scheduling a workflow

    <pre>
    consonance run  --flavour m1.xlarge \
        --image-descriptor collab.cwl \
        --run-descriptor collab-cwl-job-pre.json \
        --extra-file node-engine.cwl=node-engine.cwl=true \
        --extra-file /root/.aws/config=/home/ubuntu/.aws/config=false
    </pre>
        
This schedules a workflow to run on an instance-type of m1.xlarge, the workflow that we will run is described by collab.cwl, 
the workflow run (i.e. inputs and outputs) is described by collab-cwl-job-pre.json, we will also be loading a file called node-engine.cwl 
into the working directory, and an AWS credential file at ~root/.aws/config (necessary for uploading files to S3). 

For the extra files, note that the format is `destination=source=(whether we should retain this information)`. 

See the format for CWL on [github](https://github.com/common-workflow-language).

Insert `--quiet` after `consonance` to run in quiet mode.

Note that if you use the same extra files over and over again (e.g. all your workflows require provisioning to S3) you can describe them globally 
for all workflow runs in your ~/.consonance/config. The format of this file follows [below](#config-file-format)

For example, to send a custom config file for the [dockstore-descriptor-launcher](https://github.com/CancerCollaboratory/dockstore-descriptor#the-config-file) in order to send results to an alternate S3 api, you will need:

    extra_files = cwl-launcher.config=/home/ubuntu/cwl-launcher.config=true


2. Check on the status of a workflow

    <pre>
    consonance status --job_uuid foobar-foobar-foobar
    </pre>

This checks on the status of a workflow. 

Extract individual fields (such as the stderr) with the following one-liner:

    consonance status --job_uuid foobar-foobar-foobar | python -c 'import sys, json; print json.load(sys.stdin)["stderr"]'

#### Config File Format

    [webservice]
    base_path = http://foo.bar:8080
    token = foobar 
    
    extra_files =/root/.aws/credentials=/home/ubuntu/.aws/config=false,node-engine.cwl=node-engine.cwl=true
    
#### File Provisioning

When specifying input files in the run descriptor ( collab-cwl-job-pre.json above  ) ftp, http, https, local files can all be used out of the box with no credentials (as long as they are not protected). 
However, special credentials and config files are required for file provisioning from more exotic locations like those listed below.
 
S3 supports both input and output files.
 
DCC-storage supports only input files. 

##### S3 on AWS

Input files from AWS S3 and and output files from AWS S3 can be provisioned if you provide your an ~/.aws/config with access and secret key.
You can attach them on a per-job basis as above or define them globally in your config file. 

    --extra-file /root/.aws/config=aws_config=false

Entries in the json look as follows:

    {
      "bam_input": {
            "class": "File",
            "path": "ftp://ftp.1000genomes.ebi.ac.uk/vol1/ftp/phase3/data/NA12878/alignment/NA12878.chrom20.ILLUMINA.bwa.CEU.low_coverage.20121211.bam"
        },
        "bamstats_report": {
            "class": "File",
            "path": "s3://oicr.temp/testing-launcher/bamstats_report.zip"
        }
    }


##### S3 Api on Ceph (cancercollaboratory.org)

For alternative endpoints that support the S3 API, you can configure the endpoint you wish to use in the  [dockstore descriptor launcher](https://github.com/CancerCollaboratory/dockstore-descriptor) config. 

    --extra-file /root/.aws/config=aws_config=false --extra-file cwl-launcher.config=cwl-launcher.config=true
    
cwl-launcher will look like the following

    [s3]
    endpoint = https://www.cancercollaboratory.org:9080

##### DCC-Storage on Ceph (cancercollaboratory.org)

For the DCC-Storage system, you will need to provide a properties file for the DCC:

    --extra-file /icgc/dcc-storage/conf/application-amazon.properties=application-amazon.properties=false
    
application-amazon.properties looks like 

    logging.level.org.icgc.dcc.storage.client=DEBUG
    logging.level.org.springframework.web.client.RestTemplate=DEBUG
    client.upload.serviceHostname=storage.cancercollaboratory.org
    client.ssl.trustStore=classpath:client.jks
    accessToken=<your token here>
    
Entries in the json look as follows:

    {
      "bam_input": {
            "class": "File",
            "path": "icgc:1675bfff-4494-5b15-a96e-c97169438715 "
        },
        "bamstats_report": {
            "class": "File",
            "path": "s3://oicr.temp/testing-launcher/bamstats_report.zip"
        }
    }

 
##### DCC-Storage on AWS 

For this, simply replace the following line of the above properties file.
    
    client.upload.serviceHostname=objectstore.cancercollaboratory.org

### Admin

The administrator of the system is responsible for running three programs. They are

* web service: serves up content for the CLI does scheduling, this is backed by postgres
* co-ordinator: splits up orders and processes worker heartbeats
* container provisioner: creates VM and configures them with workflows and containers to run workflows

All three implicitly require RabbitMQ and postgres. The username and passwords should be set and match the configuration files described below.

#### Web Service

1. Get the webservice jar
2. Fill in the [config file](https://github.com/Consonance/consonance/blob/develop_2/consonance-webservice/run-fox.yml)
3. Invoke the following

    <pre>
    java -jar consonance-webservice-*.jar server ~/.stash/run-fox.yml
    </pre>
    
#### Co-ordinator and Provisoner

1. Get the arch jar
2. Fill in the [Consonance config file](https://raw.githubusercontent.com/Consonance/consonance/develop_2/consonance-arch/conf/config.json) at `~/.consonance/config`
3. Fill in the [Youxia config file](https://github.com/CloudBindle/youxia#configuration) at `~/.youxia/config`
4. Invoke the following two commands

    <pre>
    java -cp consonance-arch-*.jar io.consonance.arch.coordinator.Coordinator --config ~/.consonance/config --endless
    java -cp consonance-arch-*.jar io.consonance.arch.containerProvisior.ContainerProvisionerThreads --config ~/.consonance/config --endless
    </pre>
