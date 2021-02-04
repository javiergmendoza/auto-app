FROM tomcat:8.5-jdk11-corretto

ADD build/libs/auto-app*.war /usr/local/tomcat/webapps/auto-app.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
