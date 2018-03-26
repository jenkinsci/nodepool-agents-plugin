# The MIT License
#
# Copyright 2018 hughsaunders.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
FROM maven:3.5.3-jdk-8
ADD pom.xml pom.xml
ADD mavensettings.xml /root/.m2/settings.xml
RUN mvn dependency:resolve
RUN mvn compile
RUN apt-get update; apt-get -y install python3-dev python3-pip
RUN python3 -m pip install virtualenv
RUN python3 -m virtualenv /opt/findbugs2junit
RUN . /opt/findbugs2junit/bin/activate; pip install findbugs2junit==2018.3.23.41338
RUN mvn findbugs:check
