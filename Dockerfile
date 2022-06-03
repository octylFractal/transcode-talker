FROM openjdk:17-alpine AS gradle_build
COPY . /
RUN ["./gradlew", "-si", "build", "installDist"]

FROM openjdk:17-alpine
RUN addgroup -g 1001 -S transcode_talker && adduser -u 1001 -S transcode_talker -G transcode_talker
RUN mkdir /transcode_talker && chown -R transcode_talker:transcode_talker /transcode_talker
WORKDIR /transcode_talker
USER transcode_talker
COPY docker/start.sh .
COPY --from=gradle_build /app/build/install/app .
ENTRYPOINT ["./start.sh"]
EXPOSE 8080/tcp
