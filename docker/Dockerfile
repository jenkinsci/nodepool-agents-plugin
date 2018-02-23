FROM ubuntu:xenial

ENV NODEPOOL_REPO https://github.com/openstack-infra/nodepool
ENV NODEPOOL_SHA b7378892502836f257456cd277190ee75a7ca75f

RUN apt-get update
RUN apt-get install -y python3.5 python3-pip git build-essential curl sudo

WORKDIR /root
RUN git clone $NODEPOOL_REPO

WORKDIR /root/nodepool
RUN git checkout $NODEPOOL_SHA
RUN pip3 install -e .

RUN mkdir /opt/logs

# make common nodepool directories:
RUN mkdir /var/log/nodepool
RUN mkdir /var/run/nodepool

# deal with configuration files:
RUN mkdir /etc/nodepool
ADD nodepool.yaml /etc/nodepool/
ADD logging.conf /etc/nodepool/

WORKDIR /root

ADD launcher.sh /root
RUN chmod 755 /root/launcher.sh

ENTRYPOINT /root/launcher.sh
