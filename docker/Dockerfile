FROM centos

RUN dnf install -y openssh-server net-tools openssh-clients unzip which zip sed

RUN mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys

RUN /usr/bin/ssh-keygen -A

CMD ["/usr/sbin/sshd","-D"]
