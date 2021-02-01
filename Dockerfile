FROM openjdk:11

ENV HOME=/home/app
ENV APP_HOME=$HOME/auto-app
ENV PATH=.:$PATH
RUN mkdir -p $APP_HOME
WORKDIR $APP_HOME

COPY auto-app.conf $APP_HOME/auto-app.conf
COPY build/libs/auto-app*.jar $APP_HOME/auto-app.jar
COPY scripts/bootrunner.sh $APP_HOME/scripts/bootrunner.sh

EXPOSE 8080

CMD ["scripts/bootrunner.sh"]
