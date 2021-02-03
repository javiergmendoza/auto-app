FROM tomcat:8.5-jdk11-corretto

ENV AWS_SECRET_ACCESS_KEY=VGRwjvOpyp93OaHGudwZdoQM7z40XJJpe0IjdB50
ENV AWS_REGION=us-east-1
ENV AWS_ACCESS_KEY_ID=AKIA5MQUJJVDQE5BYNFT

ADD build/libs/auto-app*.war /usr/local/tomcat/webapps/auto-app.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
