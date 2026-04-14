# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AlertWorker is a transfer station the accept alert message and transfer message to DingDing, Email and so on. 
## Architecture

### Technology Stack

##### frontend
- vue+iview+GSAP
- The design style is simple and cozy, using soft breathing tones for the color scheme
- apply motion effects only where necessary. 

##### backend
- java+springboot+mybaits plus
- use postgres databases
- use the rabbitmq message queue 

### System terms
- **Message Channel** The notification tools which can send message to user.
   - DingDing message: send message to dingding groups via dingding webhook reboot.


- **Assignee** The assignee is responsible for recieve message, who have many attributes:

   - phone number
   - DingDing username
   - The CN name

- **Communication Group** The group contains many assignees.It also include a send type attribute which can determine the message recipients.
   - When send type is service_type. we can configure which service_type can recieved by which personnel, as well as the notification method of each person.

- **Message Template** The template has many solts and fix words.
   - use jinjia2 syntax
   - includes required attritubes and optinal attritubes

- **Alert Rule** is a toolschain specifies the alert format type (promethus or json ), the template to be applied and the corresponding communication group.
   - Can configure multiple communication groups


### High-Level Flow


1. **DataManager** present data to frontend,this service can run independently and this service is stateless.
2. **MessageGateway** recieve message,handle message and handle hooks callback:
   * 1、recieve message and handle message
   
      - extract information from various data formats, include Prometheus format、 
      - clean text
      - determine who should recieve the message base on the alert rule
      - send the message to the custom
   * 2、recieve hooks callback message and handle it
      
      - extract action and data from callback message
      - determine how to hanlde the message base on the action
      - handle the message 

   * 3、test the message channel whether can send message to the outside and test whether can send message to some body

3. **Workers**  recieve message from MQ and send it to customer:
   - send message
   - update the message status in db

### Key Components

- **Gateway Controllers** (`lib/controller/gateway/`): HTTP entry points
- **Worker Services** (`lib/service/worker/`): TTS model inference implementations
- **Worker Controllers** (`lib/controller/worker/`): HTTP endpoints for workers
- **TtsNetwork** (`lib/service/gateway/TtsNetwork.py`): Routes requests to appropriate TTS engine based on voice_id
- **Register Service** (`run_register_server.py`): Service discovery - tracks available worker nodes

### JAVA Dictionary
Use dictionary tables for static KV pairs that rarely change.

### Message Type

##### DingDing Message
DingDing Message include:
- ActionCard: Use the ActionCard message to send alert message, as well as configure the parameter of action button。
- markdown： send the Markdown message to group.
- text: send the text message to group.